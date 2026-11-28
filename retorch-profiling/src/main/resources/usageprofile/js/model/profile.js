/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Model: read-only queries over the JSON of the page (alternatives, costs, execution graph). Pure functions: no DOM, no
   translations, no state. Profile.create(data) binds them to the data of one page; the lists of phases, kinds and cloud objects
   come from data.meta (written by the generator) and fall back to the ones this page was written for. */
const Profile = (function () {
  const IDLE_PHASE = 'oversubscription';
  const DEFAULT_META = {
    phases: ['coi-setup', 'tjob-setup', 'tjob-testexec', 'tjob-teardown', 'coi-teardown', IDLE_PHASE],
    kinds: ['VM', 'CONTAINER', 'BROWSER_SERVICE'],
    objects: ['compute', 'storage', 'browser']
  };
  const CHEAPEST_COUNT = 5; // the alternatives preloaded in the comparison
  const MIN_NO_DATA = 0.03; // width of the placeholder of a TJob without measurements, as a fraction of the longest end time

  const isNum = function (v) { return typeof v === 'number'; };

  /* ---- costs ---- */
  /* Sum of the cost of the items that satisfy pred (all of them without pred). */
  function sum(items, pred) {
    return items.filter(pred || function () { return true; }).reduce(function (s, i) { return s + i.cost; }, 0);
  }

  /* Cost of the part of the VM that no TJob used. */
  function idle(a) { return sum(a.items, function (i) { return i.phase === IDLE_PHASE; }); }

  /* The meta lists may be plain names or objects with the name in label / id / name; missing lists use the defaults. */
  function names(list, fallback) {
    if (!Array.isArray(list) || !list.length) { return fallback.slice(); }
    return list.map(function (e) { return typeof e === 'string' ? e : (e && (e.label || e.id || e.name || e.key)) || ''; }).filter(Boolean);
  }

  function metaOf(data) {
    const m = data.meta || {};
    return {phases: names(m.phases, DEFAULT_META.phases), kinds: names(m.kinds, DEFAULT_META.kinds), objects: names(m.objects, DEFAULT_META.objects)};
  }

  function sorted(alts, by) {
    const order = {
      cost: function (a, b) { return a.total - b.total; },
      provider: function (a, b) { return a.provider.localeCompare(b.provider) || a.total - b.total; },
      over: function (a, b) { return idle(b) - idle(a); }
    };
    return alts.slice().sort(order[by] || order.cost);
  }

  function cheapest(alts, n) {
    return alts.slice().sort(function (a, b) { return a.total - b.total; }).slice(0, n === undefined ? CHEAPEST_COUNT : n);
  }

  /* Rows of the alternatives chart: {label, used, over} in USD or in % of the cheapest shown (opts: {showOver, metric: 'usd'|'pct'}). */
  function chartRows(alts, labelOf, opts) {
    const shown = function (a) { return opts.showOver ? a.total : a.total - idle(a); };
    const base = Math.min.apply(null, alts.map(shown).concat([Infinity]));
    const scale = function (v) { return opts.metric === 'pct' && base > 0 ? 100 * v / base : v; };
    return alts.map(function (a) { return {label: labelOf(a), used: scale(a.total - idle(a)), over: opts.showOver ? scale(idle(a)) : 0}; });
  }

  /* ---- execution graph ---- */
  function byStageThenStart(nodes) {
    return nodes.slice().sort(function (a, b) {
      return a.stage - b.stage || (isNum(a.startSec) ? a.startSec : 1e12) - (isNum(b.startSec) ? b.startSec : 1e12) ||
        (a.id < b.id ? -1 : 1);
    });
  }

  /* A TJob has measurements when its start and at least one phase duration are numbers. */
  function measured(n) {
    return isNum(n.startSec) && (isNum(n.setupSec) || isNum(n.execSec) || isNum(n.teardownSec));
  }

  /* Rows of the Gantt chart: start, segment lengths and end of every TJob, in stage order so predecessors are resolved first.
     A TJob without data is a placeholder that starts when its predecessors end. */
  function ganttLayout(nodes) {
    const rows = byStageThenStart(nodes);
    const byId = {};
    rows.forEach(function (n, i) {
      const ok = measured(n);
      const r = {node: n, row: i, noData: !ok, start: ok ? n.startSec : 0, setup: n.setupSec || 0, exec: n.execSec || 0, tear: n.teardownSec || 0};
      r.end = r.start + r.setup + r.exec + r.tear;
      byId[n.id] = r;
    });
    const longest = Math.max.apply(null, rows.map(function (n) { return byId[n.id].end; }).concat([1]));
    rows.forEach(function (n) {
      const r = byId[n.id];
      if (!r.noData) { return; }
      const ends = n.predecessors.map(function (p) { return byId[p] ? byId[p].end : 0; });
      r.start = Math.max.apply(null, ends.concat([0]));
      r.end = r.start + longest * MIN_NO_DATA;
    });
    return {rows: rows.map(function (n) { return byId[n.id]; }), byId: byId, longest: longest};
  }

  /* Sum of the measured phase durations of a TJob, or null when it has none. */
  function duration(n) {
    const parts = [n.setupSec, n.execSec, n.teardownSec].filter(isNum);
    return parts.length ? parts.reduce(function (s, v) { return s + v; }, 0) : null;
  }

  function total(list, key) { return list.reduce(function (s, c) { return s + c[key]; }, 0); }

  /* Storage of a TJob: writable layers + volumes + images; an image shared by several containers counts once. */
  function tjobStorage(list) {
    const images = {};
    list.forEach(function (c, i) { images[c.image ? c.image : '#' + i] = c.imageGb; });
    return total(list, 'layerGb') + total(list, 'volumeGb') + Object.keys(images).reduce(function (s, k) { return s + images[k]; }, 0);
  }

  /* ---- one page ---- */
  function create(data) {
    const alts = data.alternatives;
    const meta = metaOf(data);
    const graphNodes = data.graph ? data.graph.nodes : [];
    const providers = Array.from(new Set(alts.map(function (a) { return a.provider; })));
    const regions = Array.from(new Set(alts.map(function (a) { return a.region; }))).sort();
    const multiRegion = new Set(alts.filter(function (a) { return a.kind === 'VM'; })
      .map(function (a) { return a.provider + a.region; })).size > providers.length;
    const find = function (id) { return alts.find(function (a) { return a.id === id; }) || null; };
    const label = function (a) { return a.provider + ' · ' + a.label + (multiRegion ? ' [' + a.region + ']' : ''); };
    const unique = function (list) { return Array.from(new Set(list)); };

    /* Alternatives that pass the filter {offProviders: hidden providers (array or Set), kind, region} (empty kind / region = any). */
    function filtered(filter) {
      const f = filter || {};
      return alts.filter(function (a) {
        return !(f.offProviders && Array.from(f.offProviders).indexOf(a.provider) >= 0) && (!f.kind || a.kind === f.kind) && (!f.region || a.region === f.region);
      });
    }

    /* Cost of an alternative split by 'phase', 'tjob' or 'object': [{key, cost}] in display order (phases and objects as in
       meta, TJobs by name); only the keys that have items. */
    function costBy(a, dim) {
      const keys = dim === 'phase' ? meta.phases : dim === 'object' ? meta.objects : unique(a.items.map(function (i) { return i.tjob; })).sort();
      const present = unique(a.items.map(function (i) { return i[dim]; }));
      return keys.filter(function (k) { return present.indexOf(k) >= 0; })
        .map(function (k) { return {key: k, cost: sum(a.items, function (i) { return i[dim] === k; })}; });
    }

    /* Cost by two dimensions: rows and cols are the keys, values[col][row] the cost. */
    function matrix(a, rowDim, colDim, rowKeys, colKeys) {
      return {rows: rowKeys, cols: colKeys, values: colKeys.map(function (c) {
        return rowKeys.map(function (r) { return sum(a.items, function (i) { return i[rowDim] === r && i[colDim] === c; }); });
      })};
    }

    /* Cost of every alternative split by 'phase' or 'tjob': {keys, values: {key: [cost per alternative]}} (all the keys; the
       ones without cost in any alternative have only zeros). */
    function costTable(list, dim) {
      const keys = dim === 'phase' ? meta.phases
        : unique([].concat.apply([], list.map(function (a) { return a.items.map(function (i) { return i.tjob; }); }))).sort();
      const values = {};
      keys.forEach(function (k) { values[k] = list.map(function (a) { return sum(a.items, function (i) { return i[dim] === k; }); }); });
      return {keys: keys, values: values};
    }

    return {
      data: data,
      meta: meta,
      providers: function () { return providers; },
      regions: function () { return regions; },
      /* The kinds that the alternatives have, in meta order. */
      kinds: function () { return meta.kinds.filter(function (k) { return alts.some(function (a) { return a.kind === k; }); }); },
      alternatives: filtered,
      all: function () { return alts; },
      find: find,
      label: label,
      sorted: sorted,
      cheapest: function (n) { return cheapest(alts, n); },
      chartRows: function (list, opts) { return chartRows(list, label, opts); },
      costBy: costBy,
      matrix: matrix,
      costTable: costTable,
      nodes: function () { return graphNodes; },
      node: function (id) { return graphNodes.find(function (n) { return n.id === id; }) || null; },
      ganttLayout: function () { return ganttLayout(graphNodes); },
      /* Seconds the execution graph spans (0 without graph). */
      span: function () { return data.graph ? ganttLayout(graphNodes).longest : 0; },
      /* Cost items of the TJob in an alternative. */
      tjobItems: function (a, id) { return a ? a.items.filter(function (i) { return i.tjob === id; }) : []; }
    };
  }

  return {create: create, sum: sum, idle: idle, sorted: sorted, cheapest: cheapest, chartRows: chartRows, ganttLayout: ganttLayout,
    byStageThenStart: byStageThenStart, measured: measured, duration: duration, total: total, tjobStorage: tjobStorage, isNum: isNum,
    IDLE_PHASE: IDLE_PHASE, DEFAULT_META: DEFAULT_META, CHEAPEST_COUNT: CHEAPEST_COUNT};
})();

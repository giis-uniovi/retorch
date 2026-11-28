/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Controller: the only place where the model, the state and the translations meet. It builds the view-model of each view, turns what
   the user does into changes of the state (delegated events: one listener per view root, the controls carry data-act), renders only
   the views that depend on the keys that changed (KEY_UNITS), keeps the keyboard focus, announces changes to assistive technologies,
   writes the state to the URL after every change and applies a link the user navigates to.
   Actions (data-act): lang, theme, provider, kind, region, over, metric, sort, cmp-group, group, select-alt, pick, helper, select-node,
   close-panel, cont-metric; the simulator form is handled apart (debounced). */
const Controller = (function () {
  /* The units of rendering, in the order they are rendered (a partial render of a view is a unit of its own). */
  const UNIT_ORDER = ['header', 'usage', 'graph', 'panel', 'panelCost', 'panelChart', 'alternatives', 'table', 'compare', 'detail', 'sim'];
  const EVERYTHING = UNIT_ORDER.filter(function (u) { return u !== 'panelCost' && u !== 'panelChart'; });
  /* What each key of the state changes on the page. Language and theme change the texts and the colours of every view. */
  const KEY_UNITS = {
    lang: EVERYTHING, theme: EVERYTHING,
    selectedAlt: ['table', 'detail', 'panelCost'], node: ['graph', 'panel'],
    showOver: ['alternatives'], metric: ['alternatives'], sort: ['alternatives', 'table'], filters: ['alternatives', 'table'],
    compared: ['table', 'compare'], cmpGroup: ['compare'], group: ['detail'], contMetric: ['panelChart'], sim: ['sim']
  };
  /* The units whose charts depend on the container width and on the root font size. */
  const CHART_UNITS = ['usage', 'graph', 'panelChart', 'alternatives', 'compare', 'detail', 'sim'];
  const SOURCE = {ui: 'ui', hash: 'hash', start: 'start'};

  let D = null;
  let P = null;
  let ctx = null;
  let store = null;
  let lastSizeClass = '';
  const roots = {};

  const el = function (id) { return document.getElementById(id); };
  const st = function () { return store.get(); };
  const label = function (a) { return P.label(a); };
  const isCompared = function (id) { return st().compared.indexOf(id) >= 0; };

  /* ---- accessibility ---- */
  /* One polite live region for the messages that are not tied to a focus change. */
  function announce(text) {
    const live = el('live');
    live.textContent = '';
    window.requestAnimationFrame(function () { live.textContent = text; });
  }

  /* Selector of a re-creatable control (identified by a data attribute inside a known container), or null. */
  function focusPath(node) {
    const root = node && node.closest ? node.closest('#table,#cmpHelpers,#tabs,#cmpTabs') : null;
    const attr = ['data-select', 'data-helper', 'data-cmp', 'data-v'].find(function (a) { return root && node.hasAttribute(a); });
    return attr ? '#' + root.id + ' [' + attr + '="' + node.getAttribute(attr).replace(/["\\]/g, '\\$&') + '"]' : null;
  }

  /* Runs a render that re-creates controls and puts the keyboard focus back on the equivalent control. */
  function keepFocus(action) {
    const path = focusPath(document.activeElement);
    action();
    const again = path ? document.querySelector(path) : null;
    if (again) { again.focus({preventScroll: true}); }
  }

  /* ---- view-models ---- */
  function visibleAlternatives() { return P.sorted(P.alternatives(st().filters), st().sort); }

  function headerModel() {
    return {lang: st().lang, theme: st().theme, plan: D.plan, pricesFetchedAt: D.pricesFetchedAt, sut: D.sut || {}, warnings: D.warnings,
      kpis: {durationSec: D.durationSec, peakCpu: D.peakCpu, peakMemGb: D.peakMemGb, peakDiskGb: D.peakDiskGb, alternatives: D.alternatives.length}};
  }

  function alternativesModel() {
    const alts = visibleAlternatives();
    const f = st().filters;
    return {providers: P.providers().map(function (name) { return {name: name, on: f.offProviders.indexOf(name) < 0}; }), kinds: P.kinds(),
      regions: P.regions(), filters: f, showOver: st().showOver, metric: st().metric, sort: st().sort,
      rows: P.chartRows(alts, {showOver: st().showOver, metric: st().metric}), ids: alts.map(function (a) { return a.id; }),
      onSelect: function (id) { act({selectedAlt: id}, {announce: 'alt'}); }};
  }

  function tableModel() {
    return {rows: visibleAlternatives().map(function (a) {
      return {id: a.id, label: label(a), provider: a.provider, kind: a.kind, region: a.region, total: a.total, used: a.total - Profile.idle(a), over: Profile.idle(a),
        picked: a.id === st().selectedAlt, compared: isCompared(a.id)};
    })};
  }

  function compareModel() {
    const alts = D.alternatives.filter(function (a) { return isCompared(a.id); });
    const table = P.costTable(alts, st().cmpGroup);
    return {count: alts.length, group: st().cmpGroup, labels: alts.map(label), providers: alts.map(function (a) { return a.provider; }), keys: table.keys, values: table.values};
  }

  function detailModel() {
    const a = P.find(st().selectedAlt);
    if (!a) { return {alt: null}; }
    const costPhases = P.costBy(a, 'phase').filter(function (e) { return e.cost > 0; });
    const objects = P.costBy(a, 'object');
    const byPhase = st().group === 'phase';
    const phaseKeys = costPhases.map(function (e) { return e.key; });
    const categories = byPhase ? P.costBy(a, 'tjob').map(function (e) { return e.key; }) : phaseKeys;
    const stackKeys = byPhase ? phaseKeys : objects.map(function (e) { return e.key; });
    const grid = byPhase ? P.matrix(a, 'tjob', 'phase', categories, stackKeys) : P.matrix(a, 'phase', 'object', categories, stackKeys);
    return {alt: a, label: label(a), over: Profile.idle(a), group: st().group, phases: costPhases, objects: objects, categories: categories,
      stackKeys: stackKeys, values: grid.values};
  }

  function graphModel() {
    return {layout: P.ganttLayout(), nodeId: st().node, onSelect: function (id) { act({node: id}, {announce: 'node'}); }};
  }

  function panelModel() {
    const alt = P.find(st().selectedAlt);
    const node = P.node(st().node);
    return {node: node, label: alt ? label(alt) : '', provider: alt ? alt.provider : '', items: node ? P.tjobItems(alt, node.id) : [], phases: P.meta.phases, contMetric: st().contMetric};
  }

  function simModel() {
    const p = st().sim;
    const model = Simulation.compute(D.alternatives, D.durationSec, p);
    const xs = Simulation.range(Simulation.runAxis(p, Simulation.calc.maxRuns(D.durationSec, Simulation.DAY_HOURS)));
    return {sim: p, model: model, breakEven: Simulation.breakEvenRows(D.alternatives, D.durationSec, p),
      hasReservations: ctx.buyKeys.length > 0, buyKeys: ['od'].concat(ctx.buyKeys), durationSec: D.durationSec, labelOf: label,
      chart: {xs: xs, series: model.rows.slice(0, Simulation.MAX_CURVES).map(function (row) {
        return {row: row, lines: Simulation.curves(row, p, xs, D.durationSec)};
      })}};
  }

  /* ---- rendering ---- */
  const RENDER = {
    header: function () { HeaderView.render(headerModel()); },
    usage: function () { UsageView.render({timeline: D.timeline}); },
    graph: function () { if (D.graph) { GraphView.render(graphModel()); } },
    panel: function () { PanelView.render(panelModel()); },
    panelCost: function () { PanelView.renderCost(panelModel()); },
    panelChart: function () { PanelView.renderChart(panelModel()); },
    alternatives: function () { AlternativesView.render(alternativesModel()); },
    table: function () { TableView.render(tableModel()); },
    compare: function () { CompareView.render(compareModel()); },
    detail: function () { DetailView.render(detailModel()); },
    sim: function () { SimulatorView.render(simModel()); }
  };

  /* Renders the given units once, in page order (a unit that the full render of its view already covers is skipped). */
  function renderUnits(units) {
    const wanted = UNIT_ORDER.filter(function (u) { return units.indexOf(u) >= 0; });
    const skipPartial = wanted.indexOf('panel') >= 0;
    wanted.forEach(function (u) {
      if (skipPartial && (u === 'panelCost' || u === 'panelChart')) { return; }
      RENDER[u]();
    });
  }

  function unitsOf(keys) {
    return keys.reduce(function (all, key) { return all.concat(KEY_UNITS[key] || []); }, []);
  }

  function sizeClass() {
    const f = Charts.fonts('usage');
    return [f.root, f.text, f.tip, f.narrow].join('|');
  }

  /* ---- changes of the state ---- */
  /* The selected alternative is always one of those shown by the filters. */
  function keepSelectionVisible() {
    const alts = visibleAlternatives();
    if (alts.length && !alts.some(function (a) { return a.id === st().selectedAlt; })) { return store.update({selectedAlt: alts[0].id}); }
    return [];
  }

  function writeHash() {
    const text = Hash.serialize(st(), ctx);
    try { window.history.replaceState(null, '', text ? '#' + text : window.location.pathname + window.location.search); } catch (e) { /* not allowed here: the link is just not updated */ }
  }

  /* The language and the theme chosen by the user are remembered; the ones a link gives are applied only. */
  function applyPreferences(changed, source) {
    if (changed.indexOf('lang') >= 0) { I18n.set(st().lang, source === SOURCE.ui); I18n.apply(); }
    if (changed.indexOf('theme') >= 0) { Theme.set(st().theme, source === SOURCE.ui, true); }
  }

  /* After a change: preferences, the hash, the views that depend on it (but not the units in skip). Returns the changed keys. */
  function commit(changed, source, skip) {
    const all = changed.concat(keepSelectionVisible());
    if (!all.length) { return all; }
    applyPreferences(all, source);
    writeHash();
    keepFocus(function () {
      renderUnits(unitsOf(all).filter(function (u) { return (skip || []).indexOf(u) < 0; }));
    });
    return all;
  }

  /* options: announce ('alt' | 'node' | 'cmp': what to tell assistive technologies), focus ('panel' | 'item': where the focus goes
     after a TJob change), skip (units not to render: the control the user just used already shows the change), source. */
  function act(patch, options) {
    const o = options || {};
    const previousNode = st().node;
    const changed = commit(store.update(patch), o.source || SOURCE.ui, o.skip);
    if (o.announce === 'alt') {
      const alt = P.find(st().selectedAlt);
      if (alt) { announce(label(alt) + ': ' + Fmt.usd(alt.total)); }
    } else if (o.announce === 'node') {
      const node = P.node(st().node);
      announce(node ? I18n.t('graph.selected', {id: node.id, n: node.tests.length}) : I18n.t('graph.closed'));
      if (node && o.focus === 'panel') { PanelView.focusHeading(); }
      if (!node && previousNode && o.focus === 'item') { GraphView.focusItem(previousNode); }
    } else if (o.announce === 'cmp') {
      announce(I18n.t('cmp.selected', {n: st().compared.length}));
    }
    return changed;
  }

  /* ---- events ---- */
  const without = function (list, value) { return list.filter(function (x) { return x !== value; }); };
  const patchOf = function (key, value) { const p = {}; p[key] = value; return p; };

  /* data-act -> what to do. on: the event that triggers it ('click' for buttons, 'change' for controls with a value). */
  const ACTIONS = {
    'lang': {on: 'change', run: function (t) { if (t.checked) { act({lang: t.value}); } }},
    'theme': {on: 'change', run: function (t) { if (t.checked) { act({theme: t.value}); } }},
    'provider': {on: 'change', run: function (t) {
      const off = without(st().filters.offProviders, t.value);
      act({filters: {offProviders: t.checked ? off : off.concat([t.value])}});
    }},
    'kind': {on: 'change', run: function (t) { act({filters: {kind: t.value}}); }},
    'region': {on: 'change', run: function (t) { act({filters: {region: t.value}}); }},
    'over': {on: 'change', run: function (t) { act({showOver: t.checked}); }},
    'metric': {on: 'change', run: function (t) { act({metric: t.value}); }},
    'sort': {on: 'change', run: function (t) { act({sort: t.value}); }},
    'cmp-group': {on: 'click', run: function (t) { act({cmpGroup: t.getAttribute('data-v')}); }},
    'group': {on: 'click', run: function (t) { act({group: t.getAttribute('data-v')}); }},
    'select-alt': {on: 'click', run: function (t) { act({selectedAlt: t.getAttribute('data-select')}, {announce: 'alt'}); }},
    'pick': {on: 'change', run: function (t) {
      const id = t.getAttribute('data-cmp');
      const others = without(st().compared, id);
      act({compared: t.checked ? others.concat([id]) : others}, {announce: 'cmp', skip: ['table']});
    }},
    'helper': {on: 'click', run: function (t) {
      const ids = {all: ctx.altIds, none: [], cheapest: ctx.cheapestIds}[t.getAttribute('data-helper')] || [];
      act({compared: ids}, {announce: 'cmp'});
    }},
    'select-node': {on: 'click', run: function (t) { act({node: t.getAttribute('data-node')}, {announce: 'node', focus: 'panel'}); }},
    'close-panel': {on: 'click', run: function () { act({node: null}, {announce: 'node', focus: 'item'}); }},
    'cont-metric': {on: 'change', run: function (t) { act({contMetric: t.value}); }}
  };

  /* One delegated listener per root and event type: the target (or its closest ancestor) with a data-act runs its action. */
  function onEvent(e) {
    const target = e.target.closest ? e.target.closest('[data-act]') : null;
    const action = target ? ACTIONS[target.getAttribute('data-act')] : null;
    if (action && action.on === e.type) { action.run(target, e); return; }
    // a click on the row of an alternative (not on its checkbox or label) selects it
    const row = e.type === 'click' && e.target.closest ? e.target.closest('tr.alt') : null;
    if (row && !e.target.closest('input,label,button')) { act({selectedAlt: row.getAttribute('data-id')}, {announce: 'alt'}); }
  }

  /* The simulator inputs: read the form, store it, show the results (debounced); the summary is read aloud when it settles. */
  function bindSimulator(form) {
    let timer = null;
    let announceTimer = null;
    let lastAnnounced = '';
    const onInput = function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () {
        act({sim: SimulatorView.read()});
        window.clearTimeout(announceTimer);
        announceTimer = window.setTimeout(function () {
          const text = SimulatorView.summary(simModel());
          if (text && text !== lastAnnounced) { lastAnnounced = text; announce(text); }
        }, 900);
      }, 120);
    };
    form.addEventListener('input', onInput);
    form.addEventListener('change', onInput);
    form.addEventListener('submit', function (e) { e.preventDefault(); });
  }

  /* The widths and the root font size change what the charts need: the charts adapt to the size at once and are drawn again only when
     the text sizes they use changed (a root font size or breakpoint change). */
  function bindResize() {
    let timer = null;
    lastSizeClass = sizeClass();
    const later = function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () {
        Charts.resizeAll();
        const now = sizeClass();
        if (now !== lastSizeClass) {
          lastSizeClass = now;
          keepFocus(function () { renderUnits(CHART_UNITS); });
        }
      }, 150);
    };
    window.addEventListener('resize', later);
    window.addEventListener('orientationchange', later);
  }

  /* The link the user navigated to: what it does not say is the default, the user's language and theme stay. */
  function onHashChange() {
    const patch = State.resolve(Hash.parse(window.location.hash, ctx), ctx, {lang: st().lang, theme: st().theme});
    commit(store.update(patch), SOURCE.hash);
  }

  function bind() {
    ['top', 'altCard', 'tableCard', 'cmpCard', 'detail', 'graphCard'].forEach(function (id) {
      ['click', 'change'].forEach(function (type) { roots[id].addEventListener(type, onEvent); });
    });
    bindSimulator(el('simForm'));
    document.addEventListener('keydown', function (e) {
      const inside = roots.graphCard.contains(document.activeElement) || document.activeElement === document.body;
      if (e.key === 'Escape' && st().node && inside) { act({node: null}, {announce: 'node', focus: 'item'}); }
    });
    bindResize();
    window.addEventListener('hashchange', onHashChange);
  }

  /* ---- start ---- */
  function mount() {
    ['top', 'summary', 'usageCard', 'graphCard', 'graphPanel', 'altCard', 'tableCard', 'cmpCard', 'detail', 'simCard'].forEach(function (id) { roots[id] = el(id); });
    HeaderView.mount(roots.top, roots.summary);
    UsageView.mount(roots.usageCard);
    GraphView.mount(roots.graphCard);
    PanelView.mount(roots.graphPanel);
    AlternativesView.mount(roots.altCard);
    TableView.mount(roots.tableCard);
    CompareView.mount(roots.cmpCard);
    DetailView.mount(roots.detail);
    SimulatorView.mount(roots.simCard);
  }

  /* data: the JSON of the page. Order: the link is read, the page is rendered once, then the events are bound. */
  function start(data) {
    D = data;
    P = Profile.create(D);
    I18n.init();
    Theme.init(function () { renderUnits(EVERYTHING); });
    ctx = {
      langs: I18n.LANGS, themes: Theme.MODES, altIds: D.alternatives.map(function (a) { return a.id; }),
      nodeIds: P.nodes().map(function (n) { return n.id; }), providers: P.providers(), kinds: P.kinds(), regions: P.regions(),
      cheapestIds: P.cheapest().map(function (a) { return a.id; }), buyKeys: Simulation.reservationKeys(D.alternatives),
      lang: I18n.lang(), defaultLang: I18n.detect()
    };
    store = State.create(ctx);
    mount();
    const link = State.resolve(Hash.parse(window.location.hash, ctx), ctx, {lang: I18n.lang(), theme: Theme.mode()});
    store.update(link);
    keepSelectionVisible();
    applyPreferences(['lang', 'theme'], SOURCE.start);
    Charts.setTimeSpan(Math.max(D.timeline.t.length ? D.timeline.t[D.timeline.t.length - 1] : 0, P.span()));
    renderUnits(EVERYTHING);
    bind();
  }

  return {start: start};
})();

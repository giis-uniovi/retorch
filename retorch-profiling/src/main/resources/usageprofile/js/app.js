/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Page controller: state, controls, rendering of every section and deep links (#lang=es&theme=dark&node=tjobg&alt=<id>). */
const App = (function () {
  const D = JSON.parse(document.getElementById('data').textContent);
  const el = function (id) { return document.getElementById(id); };

  const S = {
    selected: D.alternatives.length ? D.alternatives[0].id : null,
    compared: new Set(), group: 'phase', cmpGroup: 'phase',
    offProviders: new Set(), kind: '', region: '',
    showOver: true, metric: 'usd', sort: 'cost', node: null
  };

  /* ---- data helpers ---- */
  const providers = Array.from(new Set(D.alternatives.map(function (a) { return a.provider; })));
  const kinds = ['VM', 'CONTAINER', 'BROWSER_SERVICE'].filter(function (k) { return D.alternatives.some(function (a) { return a.kind === k; }); });
  const regions = Array.from(new Set(D.alternatives.map(function (a) { return a.region; }))).sort();
  const multiRegion = new Set(D.alternatives.filter(function (a) { return a.kind === 'VM'; })
    .map(function (a) { return a.provider + a.region; })).size > providers.length;

  const sum = function (items, pred) { return items.filter(pred).reduce(function (s, i) { return s + i.cost; }, 0); };
  const idle = function (a) { return sum(a.items, function (i) { return i.phase === 'oversubscription'; }); };
  const label = function (a) { return a.provider + ' · ' + a.label + (multiRegion ? ' [' + a.region + ']' : ''); };
  const findAlt = function (id) { return D.alternatives.find(function (a) { return a.id === id; }); };
  const phaseName = function (p) { return I18n.t('phase.' + p); };

  function visible() {
    return D.alternatives.filter(function (a) {
      return !S.offProviders.has(a.provider) && (!S.kind || a.kind === S.kind) && (!S.region || a.region === S.region);
    });
  }

  /* The alternatives preloaded in the comparison (and chosen by its "cheapest" helper): the cheapest overall, whatever the provider. */
  const CHEAPEST_COUNT = 5;
  function cheapest() {
    return D.alternatives.slice().sort(function (a, b) { return a.total - b.total; }).slice(0, CHEAPEST_COUNT);
  }

  function sorted(alts) {
    const by = {
      cost: function (a, b) { return a.total - b.total; },
      provider: function (a, b) { return a.provider.localeCompare(b.provider) || a.total - b.total; },
      over: function (a, b) { return idle(b) - idle(a); }
    };
    return alts.slice().sort(by[S.sort]);
  }

  /* Rows of the alternatives chart in the chosen metric (USD or % of the cheapest shown). */
  function alternativeRows(alts) {
    const shown = function (a) { return S.showOver ? a.total : a.total - idle(a); };
    const base = Math.min.apply(null, alts.map(shown).concat([Infinity]));
    const scale = function (v) { return S.metric === 'pct' && base > 0 ? 100 * v / base : v; };
    return alts.map(function (a) { return {label: label(a), used: scale(a.total - idle(a)), over: S.showOver ? scale(idle(a)) : 0}; });
  }

  /* ---- accessibility helpers ---- */
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

  /* ---- controls ---- */
  const SELECTED = Html.raw(' selected');
  const CHECKED = Html.raw(' checked');
  const option = function (value, text, current) {
    return Html.t`<option value="${value}"${value === current ? SELECTED : ''}>${text}</option>`;
  };

  /* Checks the radio with this value and moves the sliding indicator of the segmented control. */
  function syncSegment(groupId, current) {
    const group = el(groupId);
    Array.prototype.forEach.call(group.querySelectorAll('input'), function (input, index) {
      input.checked = input.value === current;
      if (input.checked) { group.style.setProperty('--i', index); }
    });
  }

  function renderControls() {
    syncSegment('langGroup', I18n.lang());
    syncSegment('themeGroup', Theme.mode());
    Html.set(el('providerBoxes'), Html.join(providers.map(function (p) {
      return Html.t`<label class="chk"><input type="checkbox" value="${p}"${S.offProviders.has(p) ? '' : CHECKED}> ${p}</label>`;
    }), ' '));
    Html.set(el('kind'), [option('', I18n.t('filter.all'), S.kind)].concat(kinds.map(function (k) { return option(k, I18n.t('kind.' + k), S.kind); })));
    Html.set(el('region'), [option('', I18n.t('filter.all'), S.region)].concat(regions.map(function (r) { return option(r, r, S.region); })));
    el('showOver').checked = S.showOver;
    Html.set(el('metric'), ['usd', 'pct'].map(function (m) { return option(m, I18n.t('metric.' + m), S.metric); }));
    Html.set(el('sort'), ['cost', 'provider', 'over'].map(function (k) { return option(k, I18n.t('sort.' + k), S.sort); }));
  }

  const tabs = function (current, items) {
    return Html.join(items.map(function (t) {
      return Html.t`<button type="button" data-v="${t[0]}" class="${current === t[0] ? 'on' : ''}" aria-pressed="${String(current === t[0])}">${I18n.t(t[1])}</button>`;
    }), ' ');
  };

  /* SUT name linked to its repository (only an https address is ever linked) and branch, when the execution knows them. */
  function renderFacts() {
    const sut = D.sut || {};
    const facts = [];
    if (sut.name) {
      const link = /^https:\/\//.test(sut.url)
        ? Html.t`<a href="${sut.url}" target="_blank" rel="noopener noreferrer">${sut.name}<span class="vh"> ${I18n.t('head.newTab')}</span></a>`
        : sut.name;
      facts.push(Html.t`<span class="fact"><span class="fact-k">${I18n.t('head.sut')}</span> ${link}</span>`);
    }
    if (sut.branch) { facts.push(Html.t`<span class="fact"><span class="fact-k">${I18n.t('head.branch')}</span> <code>${sut.branch}</code></span>`); }
    Html.set(el('facts'), facts);
    el('facts').hidden = !facts.length;
  }

  function renderHead() {
    renderFacts();
    el('sub').textContent = I18n.t('app.subtitle', {plan: D.plan, date: Fmt.date(D.pricesFetchedAt)});
    document.title = I18n.t('app.title') + (D.sut && D.sut.name ? ' · ' + D.sut.name : '') + (D.plan ? ' · ' + D.plan : '');
    Html.set(el('warnings'), D.warnings.map(function (w) { return Html.t`<div class="warn">${w}</div>`; }));
    const kpis = [['kpi.duration', Fmt.dur(D.durationSec)], ['kpi.peakCpu', Fmt.num(D.peakCpu, 2) + ' vCPU'],
      ['kpi.peakMem', Fmt.num(D.peakMemGb, 2) + ' GB'], ['kpi.peakDisk', Fmt.num(D.peakDiskGb, 2) + ' GB', I18n.t('kpi.peakDiskHint')],
      ['kpi.alternatives', D.alternatives.length]];
    Html.set(el('kpis'), kpis.map(function (k) { return kpiCard(I18n.t(k[0]), k[1], k[2]); }));
  }

  const kpiCard = function (name, value, hint) {
    return Html.t`<div class="card kpi"><dt>${name}</dt><dd>${value}</dd>${hint ? Html.t`<dd class="hint">${hint}</dd>` : ''}</div>`;
  };

  /* ---- sections ---- */
  function renderAlternatives() {
    const alts = sorted(visible());
    if (alts.length && !alts.some(function (a) { return a.id === S.selected; })) { S.selected = alts[0].id; }
    renderAlternativesChart(alts);
    renderTable(alts);
  }

  function renderAlternativesChart(alts) {
    Charts.alternatives(alternativeRows(alts), {
      showOver: S.showOver, pct: S.metric === 'pct',
      onClick: function (index) { select(alts[index].id); }
    });
  }

  /* Wide: a table with sticky header. Narrow (container query in the CSS): one card per alternative, built from data-label. */
  function renderTable(alts) {
    const cols = [['table.alt', 'alt'], ['table.type', 'type'], ['table.region', 'region'], ['table.total', 'total'], ['table.used', 'used'], ['table.over', 'over']];
    const head = Html.t`<caption id="tableCaption" class="vh">${I18n.t('table.title')}</caption><thead><tr><th scope="col"><span class="vh">${I18n.t('cmp.pick')}</span></th>${
      cols.map(function (c) { return Html.t`<th scope="col">${I18n.t(c[0])}</th>`; })}</tr></thead>`;
    const cell = function (key, text) { return Html.t`<td data-label="${I18n.t('table.' + key)}">${text}</td>`; };
    const rows = alts.map(function (a) {
      const picked = a.id === S.selected;
      return Html.t`<tr class="alt ${picked ? 'sel' : ''}" data-id="${a.id}"><td class="c-pick"><label class="cell-check"><input type="checkbox" data-cmp="${a.id}" aria-label="${I18n.t('cmp.pick') + ' ' + label(a)}"${
        S.compared.has(a.id) ? CHECKED : ''}></label></td><th scope="row"><button type="button" class="link" data-select="${a.id}"${
        picked ? Html.raw(' aria-current="true"') : ''}>${label(a)}</button></th>${cell('type', I18n.t('kind.' + a.kind))}${cell('region', a.region)}${
        cell('total', Fmt.usd(a.total))}${cell('used', Fmt.usd(a.total - idle(a)))}${cell('over', Fmt.usd(idle(a)))}</tr>`;
    });
    Html.set(el('table'), Html.t`${head}<tbody>${rows}</tbody>`);
    Html.set(el('cmpHelpers'), Html.join([['all', 'cmp.all'], ['none', 'cmp.none'], ['cheapest', 'cmp.cheapest']].map(function (h) {
      return Html.t`<button type="button" data-helper="${h[0]}">${I18n.t(h[1])}</button>`;
    }), ' '));
  }

  /* Data table of a stacked chart: one row per category, one column per stack. */
  function stackTable(id, caption, headLabel, categories, stacks) {
    Charts.dataTable(id, caption, [headLabel].concat(stacks.map(function (s) { return s.name; })),
      categories.map(function (c, i) { return [c].concat(stacks.map(function (s) { return Fmt.usd(s.values[i]); })); }));
  }

  function renderCompared() {
    const alts = D.alternatives.filter(function (a) { return S.compared.has(a.id); });
    Html.set(el('cmpTabs'), tabs(S.cmpGroup, [['phase', 'cmp.byPhase'], ['tjob', 'cmp.byTjob']]));
    el('cmpCount').textContent = I18n.t('cmp.selected', {n: alts.length});
    const byPhase = S.cmpGroup === 'phase';
    const keys = byPhase ? Fmt.PHASES : Array.from(new Set([].concat.apply([], alts.map(function (a) { return a.items.map(function (i) { return i.tjob; }); })))).sort();
    const stacks = keys.map(function (k, n) {
      return {name: byPhase ? phaseName(k) : k, color: byPhase ? Fmt.PHASE_COLORS[k] : Fmt.cycleColor(n),
        pattern: byPhase ? Fmt.PHASE_PATTERNS[k] : Charts.cyclePattern(n),
        values: alts.map(function (a) { return sum(a.items, function (i) { return (byPhase ? i.phase : i.tjob) === k; }); })};
    }).filter(function (s) { return s.values.some(function (v) { return v > 0; }); });
    const labels = alts.map(label);
    const root = Charts.fonts('cmpChart').root;
    Charts.setHeight('cmpChart', Math.max(root * 14, alts.length * root * 2.4 + root * 6));
    Charts.stacked('cmpChart', {categories: labels, stacks: stacks, horizontal: true,
      description: I18n.t('cmp.title') + '. ' + I18n.t('desc.cmp', {n: alts.length})});
    stackTable('cmpData', I18n.t('cmp.title'), I18n.t('table.alt'), labels, stacks);
  }

  function pieEntries(items, keys, nameOf, colors, patterns) {
    return keys.map(function (k, n) {
      return {name: nameOf(k), value: sum(items, function (i) { return k === i.phase || k === i.object; }), color: colors[k], pattern: patterns(k, n)};
    });
  }

  function renderPies(a, phases, objects) {
    const pies = [['byPhase', 'detail.byPhase', pieEntries(a.items, phases, phaseName, Fmt.PHASE_COLORS, function (k) { return Fmt.PHASE_PATTERNS[k]; })],
      ['byObject', 'detail.byObject', pieEntries(a.items, objects, function (o) { return I18n.t('obj.' + o); }, Fmt.OBJECT_COLORS,
        function (k, n) { return Charts.cyclePattern(n * 3); })]];
    pies.forEach(function (p) {
      const total = p[2].reduce(function (s, e) { return s + e.value; }, 0);
      Charts.pie(p[0], p[2], I18n.t(p[1]));
      Charts.dataTable(p[0] + 'Data', I18n.t(p[1]), [I18n.t('data.metric'), I18n.t('data.usd'), '%'],
        p[2].map(function (e) { return [e.name, Fmt.usd(e.value), Fmt.num(total ? 100 * e.value / total : 0, 1)]; }));
    });
  }

  function renderDetail() {
    const a = findAlt(S.selected);
    if (!a) { return; }
    const over = idle(a);
    el('detailTitle').textContent = label(a) + ' (' + a.region + ')';
    Html.set(el('detailKpis'), [
      [I18n.t('detail.total'), Fmt.usd(a.total)], [I18n.t('detail.used'), Fmt.usd(a.total - over)],
      [I18n.t('detail.over'), Fmt.usd(over) + (a.total ? ' (' + Fmt.num(100 * over / a.total, 0) + '%)' : '')],
      [I18n.t('detail.price'), a.kind === 'VM' ? Fmt.usd(a.hourlyPrice) + ' ' + I18n.t('detail.perHour') : I18n.t('detail.perUse')]
    ].map(function (k) { return kpiCard(k[0], k[1]); }));

    const phases = Fmt.PHASES.filter(function (p) { return sum(a.items, function (i) { return i.phase === p; }) > 0; });
    const objects = Array.from(new Set(a.items.map(function (i) { return i.object; })));
    renderPies(a, phases, objects);
    renderBreakdown(a, phases, objects);
    Html.set(el('tabs'), tabs(S.group, [['phase', 'detail.tabPhase'], ['object', 'detail.tabObject']]));
  }

  /* Stacked bars: X = TJob stacked by phase, or X = phase stacked by cloud object. */
  function renderBreakdown(a, phases, objects) {
    const byPhase = S.group === 'phase';
    const categories = byPhase ? Array.from(new Set(a.items.map(function (i) { return i.tjob; }))).sort() : phases;
    const stackKeys = byPhase ? phases : objects;
    const stacks = stackKeys.map(function (s, n) {
      return {
        name: byPhase ? phaseName(s) : I18n.t('obj.' + s), color: byPhase ? Fmt.PHASE_COLORS[s] : Fmt.OBJECT_COLORS[s],
        pattern: byPhase ? Fmt.PHASE_PATTERNS[s] : Charts.cyclePattern(n * 3),
        values: categories.map(function (c) {
          return sum(a.items, function (i) { return byPhase ? i.tjob === c && i.phase === s : i.phase === c && i.object === s; });
        })
      };
    });
    const labels = byPhase ? categories : categories.map(phaseName);
    const title = I18n.t(byPhase ? 'detail.tabPhase' : 'detail.tabObject');
    Charts.stacked('byTjob', {categories: labels, stacks: stacks, horizontal: false, description: title + '. ' + I18n.t('desc.breakdown')});
    stackTable('byTjobData', title, I18n.t(byPhase ? 'data.tjob' : 'detail.byPhase'), labels, stacks);
  }

  function nodeById(id) {
    return D.graph ? D.graph.nodes.find(function (n) { return n.id === id; }) || null : null;
  }

  function renderGraph() {
    if (!D.graph) { return; }
    el('graphCard').hidden = false;
    const alt = findAlt(S.selected);
    Graph.render(D.graph, S.node, selectNode);
    Graph.renderList(D.graph, S.node, function (id) { selectNode(id, true); });
    Graph.renderPanel(nodeById(S.node), alt, alt ? label(alt) : '');
  }

  /* The usage chart and the execution graph share the time axis: the span is the longest of the two. */
  function renderTimeCharts() {
    const t = D.timeline.t;
    Charts.setTimeSpan(Math.max(t.length ? t[t.length - 1] : 0, D.graph ? Graph.span(D.graph) : 0));
    Charts.usage(D.timeline);
  }

  function renderAll() {
    I18n.apply();
    renderControls();
    renderHead();
    renderTimeCharts();
    renderGraph();
    renderAlternatives();
    renderCompared();
    renderDetail();
    Simulator.render();
  }

  /* Only what depends on the container width / root font size: every chart (the controls and the panel stay as they are). */
  function renderCharts() {
    keepFocus(function () {
      renderTimeCharts();
      if (D.graph) { Graph.render(D.graph, S.node, selectNode); }
      renderAlternatives();
      renderCompared();
      renderDetail();
      Graph.renderContainersChart(nodeById(S.node));
      Simulator.renderChart();
    });
  }

  /* ---- actions ---- */
  function select(id) {
    keepFocus(function () {
      S.selected = id;
      renderAlternatives();
      renderDetail();
      renderGraph();
    });
    const alt = findAlt(id);
    if (alt) { announce(label(alt) + ': ' + Fmt.usd(alt.total)); }
  }

  /* id: the TJob to open (null closes); fromList: it was chosen with the keyboard/touch list, so focus goes to the panel heading. */
  function selectNode(id, fromList) {
    const previous = S.node;
    S.node = id;
    renderGraph();
    const node = nodeById(id);
    announce(node ? I18n.t('graph.selected', {id: node.id, n: node.tests.length}) : I18n.t('graph.closed'));
    if (node && fromList === true) { Graph.focusPanel(); }
    if (!node && previous && fromList === 'restore') { Graph.focusItem(previous); }
  }

  function closePanel() { selectNode(null, 'restore'); }

  function setCompared(helper) {
    const ids = {
      all: D.alternatives.map(function (a) { return a.id; }),
      none: [],
      cheapest: cheapest().map(function (a) { return a.id; })
    }[helper] || [];
    S.compared = new Set(ids);
    keepFocus(function () {
      renderAlternatives();
      renderCompared();
    });
    announce(I18n.t('cmp.selected', {n: S.compared.size}));
  }

  function onTableChange(e) {
    const id = e.target.getAttribute('data-cmp');
    if (!id) { return; }
    if (e.target.checked) { S.compared.add(id); } else { S.compared.delete(id); }
    keepFocus(renderCompared);
    announce(I18n.t('cmp.selected', {n: S.compared.size}));
  }

  function onTableClick(e) {
    const helper = e.target.closest('[data-helper]');
    if (helper) { setCompared(helper.getAttribute('data-helper')); return; }
    const rowEl = e.target.closest('tr.alt');
    if (rowEl && !e.target.closest('input,label')) { select(rowEl.getAttribute('data-id')); }
  }

  function bindTabs(id, apply) {
    el(id).addEventListener('click', function (e) {
      const button = e.target.closest('button');
      if (button) { keepFocus(function () { apply(button.getAttribute('data-v')); }); }
    });
  }

  function bindSegment(id, apply) {
    el(id).addEventListener('change', function (e) { if (e.target.checked) { apply(e.target.value); } });
  }

  /* Re-renders the charts (their text sizes depend on the width) when the width or the root font size really changed. */
  function bindResize() {
    let timer = null;
    let lastWidth = window.innerWidth;
    let lastRoot = parseFloat(getComputedStyle(document.documentElement).fontSize);
    const later = function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () {
        const root = parseFloat(getComputedStyle(document.documentElement).fontSize);
        if (window.innerWidth !== lastWidth || root !== lastRoot) {
          lastWidth = window.innerWidth;
          lastRoot = root;
          renderCharts();
        }
      }, 150);
    };
    window.addEventListener('resize', later);
    window.addEventListener('orientationchange', later);
  }

  function bindControls() {
    bindSegment('langGroup', function (v) { I18n.set(v, true); renderAll(); });
    bindSegment('themeGroup', function (v) { Theme.set(v, true); });
    el('providers').addEventListener('change', function (e) {
      if (e.target.checked) { S.offProviders.delete(e.target.value); } else { S.offProviders.add(e.target.value); }
      renderAlternatives();
    });
    [['kind', 'kind'], ['region', 'region'], ['metric', 'metric'], ['sort', 'sort']].forEach(function (pair) {
      el(pair[0]).addEventListener('change', function (e) { S[pair[1]] = e.target.value; renderAlternatives(); });
    });
    el('showOver').addEventListener('change', function (e) { S.showOver = e.target.checked; renderAlternatives(); });
    bindTabs('tabs', function (v) { S.group = v; renderDetail(); });
    bindTabs('cmpTabs', function (v) { S.cmpGroup = v; renderCompared(); });
    el('table').addEventListener('change', onTableChange);
    el('table').addEventListener('click', onTableClick);
    el('cmpHelpers').addEventListener('click', onTableClick);
    el('graphPanel').addEventListener('click', function (e) { if (e.target.closest('#graphClose')) { closePanel(); } });
    document.addEventListener('keydown', function (e) {
      const inside = el('graphCard').contains(document.activeElement) || document.activeElement === document.body;
      if (e.key === 'Escape' && S.node && inside) { closePanel(); }
    });
    bindResize();
    window.addEventListener('hashchange', function () { applyHash(); renderAll(); });
  }

  /* ---- deep links ---- */
  function applyHash() {
    const p = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    if (p.get('lang')) { I18n.set(p.get('lang'), false); }
    if (p.get('theme')) { Theme.set(p.get('theme'), false); }
    if (p.get('alt') && findAlt(p.get('alt'))) { S.selected = p.get('alt'); }
    if (p.get('node') && nodeById(p.get('node'))) { S.node = p.get('node'); }
    if (p.get('over')) { S.showOver = p.get('over') !== '0'; }
    if (p.get('metric') === 'pct' || p.get('metric') === 'usd') { S.metric = p.get('metric'); }
    if (['cost', 'provider', 'over'].indexOf(p.get('sort')) >= 0) { S.sort = p.get('sort'); }
    if (p.get('cmpgroup') === 'tjob' || p.get('cmpgroup') === 'phase') { S.cmpGroup = p.get('cmpgroup'); }
    if (['all', 'none', 'cheapest'].indexOf(p.get('cmp')) >= 0) { S.compared = new Set(); setCompared(p.get('cmp')); }
    Simulator.applyParams(p);
  }

  function init() {
    I18n.init();
    Theme.init(function () { renderAll(); });
    S.compared = new Set(cheapest().map(function (a) { return a.id; }));
    Simulator.init(D, {label: label, announce: announce});
    applyHash();
    bindControls();
    renderAll();
  }

  return {init: init};
})();

App.init();

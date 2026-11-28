/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Page glue: builds the view-models from the model, the state and the translations, renders the views and turns what the user does
   into changes of the state. Deep links: model/hash.js. */
const App = (function () {
  const D = JSON.parse(document.getElementById('data').textContent);
  const el = function (id) { return document.getElementById(id); };

  /* ---- data (model/profile.js) ---- */
  const P = Profile.create(D);
  const phases = P.meta.phases;
  const label = P.label;

  /* ---- state (model/state.js) and deep links (model/hash.js) ---- */
  let store = null; // created by init(), once the language is known
  let ctx = null;
  const st = function () { return store.get(); };
  const isCompared = function (id) { return st().compared.indexOf(id) >= 0; };

  /* Applies a change of the state and writes it to the URL, so that a copied link reproduces the page. Returns the changed keys. */
  function set(patch) {
    const changed = store.update(patch);
    if (changed.length) { writeHash(); }
    return changed;
  }

  function writeHash() {
    const text = Hash.serialize(st(), ctx);
    try { window.history.replaceState(null, '', text ? '#' + text : window.location.pathname + window.location.search); } catch (e) { /* not allowed here: the link is just not updated */ }
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
      rows: P.chartRows(alts, {showOver: st().showOver, metric: st().metric}), ids: alts.map(function (a) { return a.id; }), onSelect: select};
  }

  function tableModel() {
    return {rows: visibleAlternatives().map(function (a) {
      return {id: a.id, label: label(a), kind: a.kind, region: a.region, total: a.total, used: a.total - Profile.idle(a), over: Profile.idle(a),
        picked: a.id === st().selectedAlt, compared: isCompared(a.id)};
    })};
  }

  function compareModel() {
    const alts = D.alternatives.filter(function (a) { return isCompared(a.id); });
    const table = P.costTable(alts, st().cmpGroup);
    return {count: alts.length, group: st().cmpGroup, labels: alts.map(label), keys: table.keys, values: table.values};
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
    return {layout: P.ganttLayout(), nodeId: st().node, onSelect: selectNode};
  }

  function panelModel() {
    const alt = P.find(st().selectedAlt);
    const node = P.node(st().node);
    return {node: node, label: alt ? label(alt) : '', items: node ? P.tjobItems(alt, node.id) : [], phases: phases, contMetric: st().contMetric};
  }

  function simModel() {
    const alternatives = D.alternatives;
    const p = st().sim;
    const model = Simulation.compute(alternatives, D.durationSec, p);
    const xs = Simulation.range(Simulation.runAxis(p, Simulation.calc.maxRuns(D.durationSec, Simulation.DAY_HOURS)));
    return {sim: p, model: model, breakEven: Simulation.breakEvenRows(alternatives, D.durationSec, p),
      hasReservations: ctx.buyKeys.length > 0, buyKeys: ['od'].concat(ctx.buyKeys), durationSec: D.durationSec, labelOf: label,
      chart: {xs: xs, series: model.rows.slice(0, Simulation.MAX_CURVES).map(function (row) {
        return {row: row, lines: Simulation.curves(row, p, xs, D.durationSec)};
      })}};
  }

  /* ---- rendering ---- */
  /* The usage chart and the execution graph share the time axis: the span is the longest of the two. */
  function renderTimeCharts() {
    const t = D.timeline.t;
    Charts.setTimeSpan(Math.max(t.length ? t[t.length - 1] : 0, P.span()));
    UsageView.render({timeline: D.timeline});
    if (D.graph) { GraphView.render(graphModel()); }
  }

  function renderGraph() {
    if (!D.graph) { return; }
    GraphView.render(graphModel());
    PanelView.render(panelModel());
  }

  function renderAlternatives() {
    const alts = visibleAlternatives();
    if (alts.length && !alts.some(function (a) { return a.id === st().selectedAlt; })) { set({selectedAlt: alts[0].id}); }
    AlternativesView.render(alternativesModel());
    TableView.render(tableModel());
  }

  function renderAll() {
    I18n.apply();
    HeaderView.render(headerModel());
    renderTimeCharts();
    PanelView.render(panelModel());
    renderAlternatives();
    CompareView.render(compareModel());
    DetailView.render(detailModel());
    SimulatorView.render(simModel());
  }

  /* Only what depends on the container width / root font size: every chart (the controls and the panel stay as they are). */
  function renderCharts() {
    keepFocus(function () {
      renderTimeCharts();
      AlternativesView.renderChart(alternativesModel());
      CompareView.render(compareModel());
      DetailView.render(detailModel());
      PanelView.renderChart(panelModel());
      SimulatorView.render(simModel());
    });
  }

  /* ---- actions ---- */
  function select(id) {
    keepFocus(function () {
      set({selectedAlt: id});
      TableView.render(tableModel());
      DetailView.render(detailModel());
      PanelView.renderCost(panelModel());
    });
    const alt = P.find(id);
    if (alt) { announce(label(alt) + ': ' + Fmt.usd(alt.total)); }
  }

  /* id: the TJob to open (null closes); fromList: it was chosen with the keyboard/touch list, so focus goes to the panel heading. */
  function selectNode(id, fromList) {
    const previous = st().node;
    set({node: id});
    renderGraph();
    const node = P.node(id);
    announce(node ? I18n.t('graph.selected', {id: node.id, n: node.tests.length}) : I18n.t('graph.closed'));
    if (node && fromList === true) { PanelView.focusHeading(); }
    if (!node && previous && fromList === 'restore') { GraphView.focusItem(previous); }
  }

  function closePanel() { selectNode(null, 'restore'); }

  function setCompared(helper) {
    const ids = {
      all: D.alternatives.map(function (a) { return a.id; }),
      none: [],
      cheapest: P.cheapest().map(function (a) { return a.id; })
    }[helper] || [];
    set({compared: ids});
    keepFocus(function () {
      TableView.render(tableModel());
      CompareView.render(compareModel());
    });
    announce(I18n.t('cmp.selected', {n: st().compared.length}));
  }

  function onTableChange(e) {
    const id = e.target.getAttribute('data-cmp');
    if (!id) { return; }
    const others = st().compared.filter(function (x) { return x !== id; });
    set({compared: e.target.checked ? others.concat([id]) : others});
    keepFocus(function () { CompareView.render(compareModel()); });
    announce(I18n.t('cmp.selected', {n: st().compared.length}));
  }

  function onTableClick(e) {
    const helper = e.target.closest('[data-helper]');
    if (helper) { setCompared(helper.getAttribute('data-helper')); return; }
    const button = e.target.closest('[data-select]');
    if (button) { select(button.getAttribute('data-select')); return; }
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

  /* The simulator inputs: read the form, store it, show the results (debounced); the summary is read aloud when it settles. */
  function bindSimulator() {
    let timer = null;
    let announceTimer = null;
    let lastAnnounced = '';
    const form = el('simForm');
    const onInput = function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () {
        set({sim: SimulatorView.read()});
        const vm = simModel();
        SimulatorView.render(vm);
        window.clearTimeout(announceTimer);
        announceTimer = window.setTimeout(function () {
          const text = SimulatorView.summary(vm);
          if (text && text !== lastAnnounced) { lastAnnounced = text; announce(text); }
        }, 900);
      }, 120);
    };
    form.addEventListener('input', onInput);
    form.addEventListener('change', onInput);
    form.addEventListener('submit', function (e) { e.preventDefault(); });
  }

  function bindControls() {
    bindSegment('langGroup', function (v) { I18n.set(v, true); set({lang: v}); renderAll(); });
    bindSegment('themeGroup', function (v) { set({theme: v}); Theme.set(v, true); });
    el('providers').addEventListener('change', function (e) {
      const others = st().filters.offProviders.filter(function (x) { return x !== e.target.value; });
      set({filters: {offProviders: e.target.checked ? others : others.concat([e.target.value])}});
      renderAlternatives();
    });
    ['kind', 'region'].forEach(function (key) {
      el(key).addEventListener('change', function (e) { const filters = {}; filters[key] = e.target.value; set({filters: filters}); renderAlternatives(); });
    });
    ['metric', 'sort'].forEach(function (key) {
      el(key).addEventListener('change', function (e) { const patch = {}; patch[key] = e.target.value; set(patch); renderAlternatives(); });
    });
    el('showOver').addEventListener('change', function (e) { set({showOver: e.target.checked}); renderAlternatives(); });
    bindTabs('tabs', function (v) { set({group: v}); DetailView.render(detailModel()); });
    bindTabs('cmpTabs', function (v) { set({cmpGroup: v}); CompareView.render(compareModel()); });
    el('table').addEventListener('change', onTableChange);
    el('table').addEventListener('click', onTableClick);
    el('cmpHelpers').addEventListener('click', onTableClick);
    el('tjobList').addEventListener('click', function (e) {
      const button = e.target.closest('button');
      if (button) { selectNode(button.getAttribute('data-node'), true); }
    });
    el('graphPanel').addEventListener('click', function (e) { if (e.target.closest('#graphClose')) { closePanel(); } });
    el('graphPanel').addEventListener('change', function (e) {
      if (e.target.name === 'contMetric') { set({contMetric: e.target.value}); PanelView.renderChart(panelModel()); }
    });
    document.addEventListener('keydown', function (e) {
      const inside = el('graphCard').contains(document.activeElement) || document.activeElement === document.body;
      if (e.key === 'Escape' && st().node && inside) { closePanel(); }
    });
    bindSimulator();
    bindResize();
    window.addEventListener('hashchange', onHashChange);
  }

  /* ---- deep links ---- */
  /* The language and the theme of a link are applied but not remembered (only the user's own choice is). */
  function applyPreferences(changed) {
    if (changed.indexOf('lang') >= 0) { I18n.set(st().lang, false); }
    if (changed.indexOf('theme') >= 0) { Theme.set(st().theme, false, true); }
  }

  /* The link the user navigated to: what it does not say is the default, the user's language and theme stay. */
  function onHashChange() {
    const changed = store.update(State.resolve(Hash.parse(window.location.hash, ctx), ctx, {lang: st().lang, theme: st().theme}));
    if (changed.length) {
      applyPreferences(changed);
      renderAll();
    }
  }

  function mountViews() {
    HeaderView.mount(el('top'), el('summary'));
    UsageView.mount(el('usageCard'));
    GraphView.mount(el('graphCard'));
    PanelView.mount(el('graphPanel'));
    AlternativesView.mount(el('altCard'));
    TableView.mount(el('tableCard'));
    CompareView.mount(el('cmpCard'));
    DetailView.mount(el('detail'));
    SimulatorView.mount(el('simCard'));
  }

  function init() {
    I18n.init();
    Theme.init(function () { renderAll(); });
    ctx = {
      langs: I18n.LANGS, themes: Theme.MODES, altIds: D.alternatives.map(function (a) { return a.id; }),
      nodeIds: P.nodes().map(function (n) { return n.id; }), providers: P.providers(), kinds: P.kinds(), regions: P.regions(),
      cheapestIds: P.cheapest().map(function (a) { return a.id; }), buyKeys: Simulation.reservationKeys(D.alternatives),
      lang: I18n.lang(), defaultLang: I18n.detect()
    };
    store = State.create(ctx);
    // 1. the link, 2. one render, 3. the events (the link never renders half a page)
    store.update(State.resolve(Hash.parse(window.location.hash, ctx), ctx, {lang: I18n.lang(), theme: Theme.mode()}));
    applyPreferences(['lang', 'theme']);
    mountViews();
    renderAll();
    bindControls();
  }

  return {init: init};
})();

App.init();

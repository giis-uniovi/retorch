/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Page controller: state, controls, rendering of every section and deep links (#lang=es&theme=dark&node=tjobg&alt=<id>). */
const App = (function () {
  const D = JSON.parse(document.getElementById('data').textContent);
  const el = function (id) { return document.getElementById(id); };

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

  /* ---- data (model/profile.js) ---- */
  const P = Profile.create(D);
  const providers = P.providers();
  const kinds = P.kinds();
  const regions = P.regions();
  const phases = P.meta.phases;
  const sum = Profile.sum;
  const idle = Profile.idle;
  const label = P.label;
  const findAlt = P.find;
  const phaseName = function (p) { return I18n.t('phase.' + p); };

  function visible() { return P.alternatives(st().filters); }

  const sorted = function (alts) { return P.sorted(alts, st().sort); };

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
      return Html.t`<label class="chk"><input type="checkbox" value="${p}"${st().filters.offProviders.indexOf(p) >= 0 ? '' : CHECKED}> ${p}</label>`;
    }), ' '));
    Html.set(el('kind'), [option('', I18n.t('filter.all'), st().filters.kind)].concat(kinds.map(function (k) { return option(k, I18n.t('kind.' + k), st().filters.kind); })));
    Html.set(el('region'), [option('', I18n.t('filter.all'), st().filters.region)].concat(regions.map(function (r) { return option(r, r, st().filters.region); })));
    el('showOver').checked = st().showOver;
    Html.set(el('metric'), ['usd', 'pct'].map(function (m) { return option(m, I18n.t('metric.' + m), st().metric); }));
    Html.set(el('sort'), ['cost', 'provider', 'over'].map(function (k) { return option(k, I18n.t('sort.' + k), st().sort); }));
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
    if (alts.length && !alts.some(function (a) { return a.id === st().selectedAlt; })) { set({selectedAlt: alts[0].id}); }
    renderAlternativesChart(alts);
    renderTable(alts);
  }

  function renderAlternativesChart(alts) {
    Charts.alternatives(P.chartRows(alts, {showOver: st().showOver, metric: st().metric}), {
      showOver: st().showOver, pct: st().metric === 'pct',
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
      const picked = a.id === st().selectedAlt;
      return Html.t`<tr class="alt ${picked ? 'sel' : ''}" data-id="${a.id}"><td class="c-pick"><label class="cell-check"><input type="checkbox" data-cmp="${a.id}" aria-label="${I18n.t('cmp.pick') + ' ' + label(a)}"${
        isCompared(a.id) ? CHECKED : ''}></label></td><th scope="row"><button type="button" class="link" data-select="${a.id}"${
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
    const alts = D.alternatives.filter(function (a) { return isCompared(a.id); });
    Html.set(el('cmpTabs'), tabs(st().cmpGroup, [['phase', 'cmp.byPhase'], ['tjob', 'cmp.byTjob']]));
    el('cmpCount').textContent = I18n.t('cmp.selected', {n: alts.length});
    const byPhase = st().cmpGroup === 'phase';
    const table = P.costTable(alts, st().cmpGroup);
    const stacks = table.keys.map(function (k, n) {
      return {name: byPhase ? phaseName(k) : k, color: byPhase ? Fmt.PHASE_COLORS[k] : Fmt.cycleColor(n),
        pattern: byPhase ? Fmt.PHASE_PATTERNS[k] : Charts.cyclePattern(n), values: table.values[k]};
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

  function renderPies(a, costPhases, objects) {
    const pies = [['byPhase', 'detail.byPhase', pieEntries(a.items, costPhases, phaseName, Fmt.PHASE_COLORS, function (k) { return Fmt.PHASE_PATTERNS[k]; })],
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
    const a = findAlt(st().selectedAlt);
    if (!a) { return; }
    const over = idle(a);
    el('detailTitle').textContent = label(a) + ' (' + a.region + ')';
    Html.set(el('detailKpis'), [
      [I18n.t('detail.total'), Fmt.usd(a.total)], [I18n.t('detail.used'), Fmt.usd(a.total - over)],
      [I18n.t('detail.over'), Fmt.usd(over) + (a.total ? ' (' + Fmt.num(100 * over / a.total, 0) + '%)' : '')],
      [I18n.t('detail.price'), a.kind === 'VM' ? Fmt.usd(a.hourlyPrice) + ' ' + I18n.t('detail.perHour') : I18n.t('detail.perUse')]
    ].map(function (k) { return kpiCard(k[0], k[1]); }));

    const costPhases = P.costBy(a, 'phase').filter(function (e) { return e.cost > 0; }).map(function (e) { return e.key; });
    const objects = P.costBy(a, 'object').map(function (e) { return e.key; });
    renderPies(a, costPhases, objects);
    renderBreakdown(a, costPhases, objects);
    Html.set(el('tabs'), tabs(st().group, [['phase', 'detail.tabPhase'], ['object', 'detail.tabObject']]));
  }

  /* Stacked bars: X = TJob stacked by phase, or X = phase stacked by cloud object. */
  function renderBreakdown(a, costPhases, objects) {
    const byPhase = st().group === 'phase';
    const categories = byPhase ? P.costBy(a, 'tjob').map(function (e) { return e.key; }) : costPhases;
    const stackKeys = byPhase ? costPhases : objects;
    const grid = byPhase ? P.matrix(a, 'tjob', 'phase', categories, stackKeys) : P.matrix(a, 'phase', 'object', categories, stackKeys);
    const stacks = stackKeys.map(function (s, n) {
      return {
        name: byPhase ? phaseName(s) : I18n.t('obj.' + s), color: byPhase ? Fmt.PHASE_COLORS[s] : Fmt.OBJECT_COLORS[s],
        pattern: byPhase ? Fmt.PHASE_PATTERNS[s] : Charts.cyclePattern(n * 3), values: grid.values[n]
      };
    });
    const labels = byPhase ? categories : categories.map(phaseName);
    const title = I18n.t(byPhase ? 'detail.tabPhase' : 'detail.tabObject');
    Charts.stacked('byTjob', {categories: labels, stacks: stacks, horizontal: false, description: title + '. ' + I18n.t('desc.breakdown')});
    stackTable('byTjobData', title, I18n.t(byPhase ? 'data.tjob' : 'detail.byPhase'), labels, stacks);
  }

  const nodeById = P.node;

  function renderGraph() {
    if (!D.graph) { return; }
    el('graphCard').hidden = false;
    const alt = findAlt(st().selectedAlt);
    Graph.render(D.graph, st().node, selectNode);
    Graph.renderList(D.graph, st().node, function (id) { selectNode(id, true); });
    renderPanel(alt);
  }

  function renderPanel(alt) {
    Graph.renderPanel(nodeById(st().node), alt, alt ? label(alt) : '', phases, st().contMetric, function (metric) {
      set({contMetric: metric});
      Graph.renderContainersChart(nodeById(st().node), st().contMetric);
    });
  }

  /* The usage chart and the execution graph share the time axis: the span is the longest of the two. */
  function renderTimeCharts() {
    const t = D.timeline.t;
    Charts.setTimeSpan(Math.max(t.length ? t[t.length - 1] : 0, P.span()));
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
      if (D.graph) { Graph.render(D.graph, st().node, selectNode); }
      renderAlternatives();
      renderCompared();
      renderDetail();
      Graph.renderContainersChart(nodeById(st().node), st().contMetric);
      Simulator.renderChart();
    });
  }

  /* ---- actions ---- */
  function select(id) {
    keepFocus(function () {
      set({selectedAlt: id});
      renderAlternatives();
      renderDetail();
      renderGraph();
    });
    const alt = findAlt(id);
    if (alt) { announce(label(alt) + ': ' + Fmt.usd(alt.total)); }
  }

  /* id: the TJob to open (null closes); fromList: it was chosen with the keyboard/touch list, so focus goes to the panel heading. */
  function selectNode(id, fromList) {
    const previous = st().node;
    set({node: id});
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
      cheapest: P.cheapest().map(function (a) { return a.id; })
    }[helper] || [];
    set({compared: ids});
    keepFocus(function () {
      renderAlternatives();
      renderCompared();
    });
    announce(I18n.t('cmp.selected', {n: st().compared.length}));
  }

  function onTableChange(e) {
    const id = e.target.getAttribute('data-cmp');
    if (!id) { return; }
    const others = st().compared.filter(function (x) { return x !== id; });
    set({compared: e.target.checked ? others.concat([id]) : others});
    keepFocus(renderCompared);
    announce(I18n.t('cmp.selected', {n: st().compared.length}));
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
    bindTabs('tabs', function (v) { set({group: v}); renderDetail(); });
    bindTabs('cmpTabs', function (v) { set({cmpGroup: v}); renderCompared(); });
    el('table').addEventListener('change', onTableChange);
    el('table').addEventListener('click', onTableClick);
    el('cmpHelpers').addEventListener('click', onTableClick);
    el('graphPanel').addEventListener('click', function (e) { if (e.target.closest('#graphClose')) { closePanel(); } });
    document.addEventListener('keydown', function (e) {
      const inside = el('graphCard').contains(document.activeElement) || document.activeElement === document.body;
      if (e.key === 'Escape' && st().node && inside) { closePanel(); }
    });
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

  function init() {
    I18n.init();
    Theme.init(function () { renderAll(); });
    ctx = {
      langs: I18n.LANGS, themes: Theme.MODES, altIds: D.alternatives.map(function (a) { return a.id; }),
      nodeIds: P.nodes().map(function (n) { return n.id; }), providers: providers, kinds: kinds, regions: regions,
      cheapestIds: P.cheapest().map(function (a) { return a.id; }), buyKeys: Simulation.reservationKeys(D.alternatives),
      lang: I18n.lang(), defaultLang: I18n.detect()
    };
    store = State.create(ctx);
    // 1. the link, 2. one render, 3. the events (the link never renders half a page)
    const all = ['lang', 'theme'];
    store.update(State.resolve(Hash.parse(window.location.hash, ctx), ctx, {lang: I18n.lang(), theme: Theme.mode()}));
    applyPreferences(all);
    Simulator.init(D, {label: label, announce: announce, get: function () { return st().sim; }, set: function (sim) { set({sim: sim}); }});
    renderAll();
    bindControls();
  }

  return {init: init};
})();

App.init();

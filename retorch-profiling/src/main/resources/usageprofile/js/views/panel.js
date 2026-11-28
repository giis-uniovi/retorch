/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the panel below the execution graph with the details of the selected TJob: stage, predecessors, resources, tests executed,
   the chart per container, measurements, containers and the cost of the TJob in the selected alternative. It is hidden until a TJob
   is selected. Three renders, so that a change only rewrites what it affects: render (the whole panel), renderCost (another
   alternative selected: the cost section only, the open containers stay open) and renderChart (another resource for the chart).
   vm: {node: graph node or null, label: label of the selected alternative, provider and kind: its icons, items: its cost items of this TJob, phases: phase names in
   display order, contMetric: 'storage'|'mem'|'cpu'}. */
const PanelView = (function () {
  /* What the chart below the containers can show, chosen by the user: id, label key, stacked?, unit, series [label key, field, colour index]. */
  const METRICS = [
    {id: 'storage', label: 'graph.sDisk', stacked: true, unit: 'GB',
      series: [['graph.layer', 'layerGb', 0], ['graph.imageSize', 'imageGb', 3], ['graph.volumes', 'volumeGb', 2]]},
    {id: 'mem', label: 'graph.sMem', stacked: false, unit: 'GB', series: [['graph.cMemPeak', 'peakMemGb', 1], ['graph.cMemAvg', 'avgMemGb', 4]]},
    {id: 'cpu', label: 'graph.sCpu', stacked: false, unit: 'vCPU', series: [['graph.cCpuPeak', 'peakCpu', 0], ['graph.cCpuAvg', 'avgCpu', 5]]}];
  const CONTAINER_COLS = [['graph.cCpuPeak', 'peakCpu', 'vCPU'], ['graph.cCpuAvg', 'avgCpu', 'vCPU'], ['graph.cMemPeak', 'peakMemGb', 'GB'],
    ['graph.cMemAvg', 'avgMemGb', 'GB'], ['graph.layer', 'layerGb', 'GB'], ['graph.imageSize', 'imageGb', 'GB'], ['graph.volumes', 'volumeGb', 'GB'],
    ['graph.cStorage', 'storageGb', 'GB']];

  let root = null;

  function mount(rootEl) { root = rootEl; }

  const gb = function (v) { return Fmt.num(v, v > 0 && v < 0.1 ? 3 : 2); };

  function row(labelKey, value) {
    return Html.t`<dt>${I18n.t(labelKey)}</dt><dd>${value}</dd>`;
  }

  function costRows(vm) {
    const all = Profile.sum(vm.items);
    if (!all) { return Html.t`<p class="muted">${I18n.t('graph.noCost')}</p>`; }
    const colors = Theme.colors().phase;
    const rows = vm.phases.map(function (phase) {
      const sum = Profile.sum(vm.items, function (i) { return i.phase === phase; });
      return sum > 0 ? Html.t`<tr><th scope="row"><span class="dot" aria-hidden="true" style="background:${colors[phase]}"></span>${
        I18n.t('phase.' + phase)}</th><td>${Fmt.usd(sum)}</td></tr>` : '';
    });
    return Html.t`<table class="mini"><caption class="vh">${I18n.t('graph.cost', {alt: vm.label || ''})}</caption><tbody>${rows}<tr class="total"><th scope="row">${
      I18n.t('graph.total')}</th><td>${Fmt.usd(all)}</td></tr></tbody></table>`;
  }

  function costSection(vm) {
    return Html.t`<h4>${Icons.both(vm.provider, vm.kind)}${I18n.t('graph.cost', {alt: vm.label || ''})}</h4>${costRows(vm)}`;
  }

  function measures(n) {
    return Html.t`<dl class="measures">${[row('graph.start', Fmt.orDash(n.startSec, Fmt.dur)), row('graph.setup', Fmt.orDash(n.setupSec, Fmt.dur)),
      row('graph.exec', Fmt.orDash(n.execSec, Fmt.dur)), row('graph.teardown', Fmt.orDash(n.teardownSec, Fmt.dur)),
      row('graph.duration', Fmt.orDash(Profile.duration(n), Fmt.dur)),
      row('graph.peakCpu', Fmt.orDash(n.peakCpu, function (v) { return Fmt.num(v, 2) + ' vCPU'; })),
      row('graph.peakMem', Fmt.orDash(n.peakMemGb, function (v) { return Fmt.num(v, 2) + ' GB'; }))]}</dl>`;
  }

  function testList(n) {
    if (!n.tests.length) { return Html.t`<p class="muted">${I18n.t('graph.noTests')}</p>`; }
    return Html.t`<div class="tests-scroll" role="region" tabindex="0" aria-labelledby="graphTestsTitle"><ul class="tests">${
      n.tests.map(function (t) { return Html.t`<li>${t}</li>`; })}</ul></div>`;
  }

  function chips(list) {
    return list.length ? Html.join(list.map(function (r) { return Html.t`<span class="chip">${r}</span>`; }), ' ') : '–';
  }

  /* ---- resources of the TJob: one row (card on narrow panels) per container, plus the chart ---- */
  function containerValue(col, c) {
    return col[2] === 'vCPU' ? Fmt.num(c[col[1]], 2) : gb(c[col[1]]);
  }

  /* One line with the key figures, always visible; the full figures are in the body of the <details>. */
  function containerSummary(c) {
    const figures = [I18n.t('graph.sCpu') + ' ' + containerValue(CONTAINER_COLS[0], c) + ' vCPU',
      I18n.t('graph.sMem') + ' ' + containerValue(CONTAINER_COLS[2], c) + ' GB',
      I18n.t('graph.sDisk') + ' ' + containerValue(CONTAINER_COLS[7], c) + ' GB'];
    return Html.t`<span class="name">${c.name}</span><span class="cont-sum">${figures.join(' · ')}</span>`;
  }

  /* A container is collapsed until the user opens it (native disclosure: keyboard and screen reader support for free). */
  function containerItem(c) {
    const rows = CONTAINER_COLS.map(function (col) {
      return Html.t`<dt>${I18n.t(col[0])}</dt><dd>${containerValue(col, c) + ' ' + col[2]}</dd>`;
    });
    const image = c.image ? Html.t`<p class="sub-name"><span class="muted">${I18n.t('graph.cImage')}:</span> ${c.image}</p>` : '';
    return Html.t`<details class="cont-item"><summary>${containerSummary(c)}</summary>${image}<dl class="measures">${rows}</dl></details>`;
  }

  function containerNotes(n) {
    const list = n.containers;
    const orDash = function (v, unit) { return Fmt.orDash(v, function (x) { return Fmt.num(x, 2) + ' ' + unit; }); };
    return Html.t`<p class="muted small">${I18n.t('graph.cPeaks', {cpu: Fmt.num(Profile.total(list, 'peakCpu'), 2) + ' vCPU', mem: Fmt.num(Profile.total(list, 'peakMemGb'), 2) + ' GB',
      tcpu: orDash(n.peakCpu, 'vCPU'), tmem: orDash(n.peakMemGb, 'GB')})}</p><p class="muted small">${
      I18n.t('graph.cStorageTotal', {gb: gb(Profile.tjobStorage(list)) + ' GB'})}</p>`;
  }

  function containerSection(n) {
    const list = n.containers || [];
    const title = Html.t`<h4 id="graphContTitle">${I18n.t('graph.containers')}</h4>`;
    if (!list.length) { return Html.t`${title}<p class="muted">${I18n.t('graph.noContainers')}</p>`; }
    return Html.t`${title}${containerNotes(n)}<div class="cont-list" role="group" aria-labelledby="graphContTitle">${list.map(containerItem)}</div>`;
  }

  /* Always visible, right after the tests executed and before the measurements. */
  function containerChartSection(vm) {
    if (!(vm.node.containers || []).length) { return ''; }
    const picks = METRICS.map(function (m) {
      return Html.t`<label><input type="radio" name="contMetric" data-act="cont-metric" value="${m.id}"${m.id === vm.contMetric ? Html.raw(' checked') : ''}><span>${I18n.t(m.label)}</span></label>`;
    });
    return Html.t`<div id="graphContChartBox"><h4>${I18n.t('graph.cChart')}</h4><div class="metric-pick" role="radiogroup" aria-label="${I18n.t('graph.mLabel')}">${
      picks}</div><div class="chart cont-chart" id="graphContChart" role="img"></div></div>`;
  }

  /* One bar group per container for the chosen resource: storage stacked (layer + image + volumes), memory and CPU as peak / average.
     The list of containers holds the same figures. */
  function renderChart(vm) {
    if (!vm.node || !View.$(root, 'graphContChartBox')) { return; } // nodes without containers have no chart
    Array.prototype.forEach.call(root.querySelectorAll('input[name="contMetric"]'), function (r) { r.checked = r.value === vm.contMetric; });
    const list = vm.node.containers;
    const f = Charts.fonts('graphContChart');
    const m = METRICS.filter(function (x) { return x.id === vm.contMetric; })[0];
    const key = m.series[0][1];
    const largest = list.reduce(function (best, c) { return c[key] > best[key] ? c : best; }, list[0]);
    const top = m.stacked ? gb(largest.storageGb) + ' GB' : Fmt.num(largest[key], 3) + ' ' + m.unit;
    Charts.setHeight('graphContChart', Math.max(f.root * 11, list.length * f.root * (m.stacked ? 2.4 : 3.2) + f.root * 7));
    Charts.stacked('graphContChart', {
      categories: list.map(function (c) { return c.name; }), horizontal: true, axisName: m.unit, grouped: !m.stacked,
      format: function (v) { return Fmt.num(v, 3) + ' ' + m.unit; },
      stacks: m.series.map(function (s, n) {
        return {name: I18n.t(s[0]), color: Fmt.cycleColor(s[2]), pattern: Charts.cyclePattern(n), values: list.map(function (c) { return c[s[1]]; })};
      }),
      description: I18n.t('graph.cChart') + '. ' + (m.stacked
        ? I18n.t('desc.containers', {n: list.length, name: largest.name, gb: top})
        : I18n.t('desc.containersPeak', {metric: I18n.t(m.label), n: list.length, name: largest.name, v: top}))
    });
  }

  function render(vm) {
    root.hidden = !vm.node;
    Charts.dispose('graphContChart');
    if (!vm.node) {
      Html.set(root, '');
      return;
    }
    const n = vm.node;
    const closeText = I18n.t('graph.close');
    Html.set(root, Html.t`<div class="panel-head"><h3 id="graphPanelTitle" tabindex="-1">${n.id}</h3><button type="button" id="graphClose" class="icon" data-act="close-panel" title="${closeText}" aria-label="${closeText}">×</button></div><div class="muted">${
      I18n.t('graph.stage', {n: n.stage})}</div>${Profile.measured(n) ? '' : Html.t`<div class="warn small">${I18n.t('graph.noData')}</div>`}${
      n.predecessors.length ? Html.t`<h4>${I18n.t('graph.predecessors')}</h4><div>${chips(n.predecessors)}</div>` : ''}<h4>${I18n.t('graph.resources')}</h4><div>${
      chips(n.resources)}</div><h4 id="graphTestsTitle">${I18n.t('graph.tests') + ' (' + n.tests.length + ')'}</h4>${testList(n)}${containerChartSection(vm)}<h4>${
      I18n.t('graph.measures')}</h4>${measures(n)}${containerSection(n)}<div id="graphCost">${costSection(vm)}</div>`);
    renderChart(vm);
  }

  /* Another alternative is selected: only the cost of the TJob changes. */
  function renderCost(vm) {
    const box = View.$(root, 'graphCost');
    if (box && vm.node) { Html.set(box, costSection(vm)); }
  }

  /* The heading of the panel takes the focus when a TJob is chosen from the list. */
  function focusHeading() {
    const heading = View.$(root, 'graphPanelTitle');
    if (heading) { heading.focus(); }
  }

  function resize() { Charts.resize('graphContChart'); }

  return {mount: mount, render: render, renderCost: renderCost, renderChart: renderChart, focusHeading: focusHeading, resize: resize};
})();

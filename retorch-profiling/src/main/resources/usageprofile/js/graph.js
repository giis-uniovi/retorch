/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Execution graph drawn as a Gantt chart (bar length = TJob duration, three segments = setup / tests / teardown),
   the keyboard-accessible list of TJobs and the side panel with the details of the selected TJob. The dependencies are
   not drawn (the arrows cluttered the chart); the panel lists the predecessors of the selected TJob. */
const Graph = (function () {
  const MIN_NO_DATA = 0.03; // placeholder width as a fraction of the longest end time
  const SEGMENTS = [['graph.setup', 'tjob-setup'], ['graph.exec', 'tjob-testexec'], ['graph.teardown', 'tjob-teardown']];

  const isNum = function (v) { return typeof v === 'number'; };

  function byStageThenStart(nodes) {
    return nodes.slice().sort(function (a, b) {
      return a.stage - b.stage || (isNum(a.startSec) ? a.startSec : 1e12) - (isNum(b.startSec) ? b.startSec : 1e12) ||
        (a.id < b.id ? -1 : 1);
    });
  }

  function measured(n) {
    return isNum(n.startSec) && (isNum(n.setupSec) || isNum(n.execSec) || isNum(n.teardownSec));
  }

  /* Adds start / segment ends / end / noData to each node, in stage order so predecessors are resolved first. */
  function layout(nodes) {
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

  function value(r) {
    return [r.row, r.start, r.start + r.setup, r.start + r.setup + r.exec, r.end, r.node.id, r.noData];
  }

  function rect(api, x1, x2, y, h, style) {
    const a = api.coord([x1, y]);
    const b = api.coord([x2, y]);
    return {type: 'rect', shape: {x: a[0], y: a[1] - h / 2, width: Math.max(1, b[0] - a[0]), height: h}, style: style};
  }

  /* renderItem of the custom series: three coloured and patterned rectangles (or one grey placeholder) plus the selection outline. */
  function bar(selectedId) {
    return function (params, api) {
      const v = {
        row: api.value(0), s: api.value(1), a: api.value(2), b: api.value(3), e: api.value(4), id: api.value(5), noData: api.value(6)
      };
      const h = api.size([0, 1])[1] * 0.62;
      const c = Theme.colors();
      const fill = function (i) { return Charts.pattern(Fmt.PHASE_PATTERNS[SEGMENTS[i][1]], Fmt.PHASE_COLORS[SEGMENTS[i][1]]); };
      const children = v.noData
        ? [rect(api, v.s, v.e, v.row, h, {fill: c.line, stroke: c.muted, lineDash: [4, 3], lineWidth: 1})]
        : [rect(api, v.s, v.a, v.row, h, {fill: fill(0)}), rect(api, v.a, v.b, v.row, h, {fill: fill(1)}),
          rect(api, v.b, v.e, v.row, h, {fill: fill(2)})];
      if (v.id === selectedId) { children.push(rect(api, v.s, v.e, v.row, h + 6, {fill: 'none', stroke: c.fg, lineWidth: 2.5})); }
      return {type: 'group', children: children};
    };
  }

  function stageBands(rows, f) {
    const spans = [];
    rows.forEach(function (r) {
      const last = spans[spans.length - 1];
      if (last && last.stage === r.node.stage) { last.to = r.node.id; } else { spans.push({stage: r.node.stage, from: r.node.id, to: r.node.id}); }
    });
    return spans.map(function (s, i) {
      return [{yAxis: s.from, name: I18n.t('graph.stage', {n: s.stage}), itemStyle: {color: i % 2 ? 'rgba(128,128,128,0.10)' : 'rgba(128,128,128,0.04)'},
        label: {position: 'insideRight', color: Theme.colors().muted, fontSize: f.text}}, {yAxis: s.to}];
    });
  }

  function legendSeries(rows, f) {
    return SEGMENTS.map(function (p, i) {
      const s = {name: I18n.t(p[0]), type: 'bar', data: [], color: Fmt.PHASE_COLORS[p[1]], itemStyle: {decal: Charts.decal(Fmt.PHASE_PATTERNS[p[1]])}};
      if (i === 0) { s.markArea = {silent: true, data: stageBands(rows, f)}; }
      return s;
    });
  }

  function tooltip(lay) {
    return function (p) {
      if (p.seriesType !== 'custom') { return ''; }
      const r = lay.byId[p.data[5]];
      const lines = [Html.t`<b>${r.node.id}</b> · ${I18n.t('graph.stage', {n: r.node.stage})}`];
      if (r.noData) { return Html.str(Html.join(lines.concat([Html.t`<i>${I18n.t('graph.noData')}</i>`]), '<br>')); }
      return Html.str(Html.join(lines.concat([
        Html.t`${I18n.t('graph.start')}: ${Fmt.dur(r.start)}`, Html.t`${I18n.t('graph.setup')}: ${Fmt.dur(r.setup)}`,
        Html.t`${I18n.t('graph.exec')}: ${Fmt.dur(r.exec)}`, Html.t`${I18n.t('graph.teardown')}: ${Fmt.dur(r.tear)}`,
        Html.t`<b>${I18n.t('graph.duration')}: ${Fmt.dur(r.end - r.start)}</b>`]), '<br>'));
    };
  }

  function describeChart(lay) {
    const stages = new Set(lay.rows.map(function (r) { return r.node.stage; })).size;
    Charts.describe('graphChart', I18n.t('graph.title') + '. ' + I18n.t('desc.graph', {n: lay.rows.length, stages: stages, dur: Fmt.dur(lay.longest)}));
  }

  let selectCallback = null;

  /* The side panel only takes room when a TJob is selected: without selection the chart uses the whole width and a hint
     tells how to select one. Done before the chart is created so that it measures its final width. */
  function showPanelArea(selected) {
    document.getElementById('graphPanel').hidden = !selected;
    document.getElementById('graphHint').hidden = selected;
  }

  /* graph: {nodes}; selectedId: id or null; onSelect(id|null) is called when the user clicks a bar / the background. */
  function render(graph, selectedId, onSelect) {
    showPanelArea(!!selectedId);
    const lay = layout(graph.nodes);
    const f = Charts.fonts('graphChart');
    Charts.setHeight('graphChart', lay.rows.length * Math.round(f.root * 2.7) + f.root * 7);
    const c = Theme.colors();
    const frame = Charts.timeFrame(f);
    const chart = Charts.mk('graphChart', {
      aria: Charts.ARIA_DECAL,
      tooltip: {trigger: 'item', formatter: tooltip(lay)},
      legend: Charts.legend(f, {top: 0, selectedMode: false, data: SEGMENTS.map(function (s) { return I18n.t(s[0]); })}),
      // same plot margins, time range and zoom as the usage chart: the two charts line up (see Charts.timeFrame)
      grid: {left: frame.left, right: frame.right, top: f.root * 2.4, bottom: f.root * 4},
      xAxis: Charts.timeAxis(f, {name: I18n.t('graph.time'), nameLocation: 'middle', nameGap: f.root * 1.8}),
      yAxis: Charts.axis(f, {type: 'category', inverse: true, data: lay.rows.map(function (r) { return r.node.id; }), splitLine: {show: false},
        axisLabel: {color: c.muted, fontSize: f.text, width: frame.left - 12, overflow: 'truncate'}}),
      dataZoom: Charts.timeZoom(f),
      series: legendSeries(lay.rows, f).concat([
        {type: 'custom', name: 'tjobs', renderItem: bar(selectedId), encode: {x: [1, 4], y: 0}, z: 4,
          data: lay.rows.map(value)}])
    });
    Charts.linkZoom('graphChart');
    describeChart(lay);
    // chart.off('click') only removes the chart level handlers. Never call getZr().off('click') without the handler:
    // it also removes the internal handler ECharts uses to dispatch the series events, and clicks on a bar stop working.
    chart.off('click');
    chart.on('click', function (p) { if (p.seriesType === 'custom') { onSelect(p.data[5]); } });
    selectCallback = onSelect;
    if (!chart.backgroundClickBound) {
      chart.backgroundClickBound = true;
      chart.getZr().on('click', function (e) { if (!e.target && selectCallback) { selectCallback(null); } });
    }
  }

  /* ---- keyboard / touch path: one button per TJob, selects exactly like clicking its bar ---- */
  let listKey = '';

  function itemText(n) {
    const parts = [n.setupSec, n.execSec, n.teardownSec].filter(isNum);
    const total = parts.length ? parts.reduce(function (s, v) { return s + v; }, 0) : null;
    return n.id + ' · ' + I18n.t('graph.stage', {n: n.stage}) + ' · ' + Fmt.orDash(total, Fmt.dur);
  }

  function renderList(graph, selectedId, onSelect) {
    const list = document.getElementById('tjobList');
    const nodes = byStageThenStart(graph.nodes);
    const key = I18n.lang() + '|' + nodes.map(function (n) { return n.id; }).join(',');
    if (key !== listKey) {
      listKey = key;
      Html.set(list, nodes.map(function (n) {
        return Html.t`<li><button type="button" data-node="${n.id}">${itemText(n)}</button></li>`;
      }));
    }
    list.onclick = function (e) {
      const button = e.target.closest('button');
      if (button) { onSelect(button.getAttribute('data-node')); }
    };
    Array.prototype.forEach.call(list.querySelectorAll('button'), function (b) {
      if (b.getAttribute('data-node') === selectedId) { b.setAttribute('aria-current', 'true'); } else { b.removeAttribute('aria-current'); }
    });
  }

  function focusItem(id) {
    const list = document.getElementById('tjobList');
    const target = Array.prototype.find.call(list.querySelectorAll('button'), function (b) { return b.getAttribute('data-node') === id; });
    if (target) { target.focus(); }
  }

  function focusPanel() {
    const heading = document.getElementById('graphPanelTitle');
    if (heading) { heading.focus(); }
  }

  /* ---- side panel ---- */
  function row(labelKey, value) {
    return Html.t`<dt>${I18n.t(labelKey)}</dt><dd>${value}</dd>`;
  }

  function costRows(items, caption) {
    const total = items.reduce(function (s, i) { return s + i.cost; }, 0);
    if (!total) { return Html.t`<p class="muted">${I18n.t('graph.noCost')}</p>`; }
    const rows = Fmt.PHASES.map(function (phase) {
      const sum = items.filter(function (i) { return i.phase === phase; }).reduce(function (s, i) { return s + i.cost; }, 0);
      return sum > 0 ? Html.t`<tr><th scope="row"><span class="dot" aria-hidden="true" style="background:${Fmt.PHASE_COLORS[phase]}"></span>${
        I18n.t('phase.' + phase)}</th><td>${Fmt.usd(sum)}</td></tr>` : '';
    });
    return Html.t`<table class="mini"><caption class="vh">${caption}</caption><tbody>${rows}<tr class="total"><th scope="row">${
      I18n.t('graph.total')}</th><td>${Fmt.usd(total)}</td></tr></tbody></table>`;
  }

  function measures(n) {
    const parts = [n.setupSec, n.execSec, n.teardownSec].filter(isNum);
    const total = parts.length ? parts.reduce(function (s, v) { return s + v; }, 0) : null;
    return Html.t`<dl class="measures">${[row('graph.start', Fmt.orDash(n.startSec, Fmt.dur)), row('graph.setup', Fmt.orDash(n.setupSec, Fmt.dur)),
      row('graph.exec', Fmt.orDash(n.execSec, Fmt.dur)), row('graph.teardown', Fmt.orDash(n.teardownSec, Fmt.dur)),
      row('graph.duration', Fmt.orDash(total, Fmt.dur)),
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

  /* ---- resources of the TJob: one row (card on narrow panels) per container, plus the storage split chart ---- */
  const CONTAINER_COLS = [['graph.cCpuPeak', 'peakCpu', 'vCPU'], ['graph.cCpuAvg', 'avgCpu', 'vCPU'], ['graph.cMemPeak', 'peakMemGb', 'GB'],
    ['graph.cMemAvg', 'avgMemGb', 'GB'], ['graph.layer', 'layerGb', 'GB'], ['graph.imageSize', 'imageGb', 'GB'], ['graph.volumes', 'volumeGb', 'GB'],
    ['graph.cStorage', 'storageGb', 'GB']];
  /* What the chart below the containers can show, chosen by the user: [id, label key, series [label key, field, colour, unit], stacked?]. */
  const METRICS = [
    {id: 'storage', label: 'graph.sDisk', stacked: true, unit: 'GB',
      series: [['graph.layer', 'layerGb', 0], ['graph.imageSize', 'imageGb', 3], ['graph.volumes', 'volumeGb', 2]]},
    {id: 'mem', label: 'graph.sMem', stacked: false, unit: 'GB', series: [['graph.cMemPeak', 'peakMemGb', 1], ['graph.cMemAvg', 'avgMemGb', 4]]},
    {id: 'cpu', label: 'graph.sCpu', stacked: false, unit: 'vCPU', series: [['graph.cCpuPeak', 'peakCpu', 0], ['graph.cCpuAvg', 'avgCpu', 5]]}];
  let metricId = 'storage'; // kept while the user moves between TJobs

  const gb = function (v) { return Fmt.num(v, v > 0 && v < 0.1 ? 3 : 2); };
  const total = function (list, key) { return list.reduce(function (s, c) { return s + c[key]; }, 0); };

  /* Writable layers + volumes + images; an image shared by several containers counts once. */
  function tjobStorage(list) {
    const images = {};
    list.forEach(function (c, i) { images[c.image ? c.image : '#' + i] = c.imageGb; });
    return total(list, 'layerGb') + total(list, 'volumeGb') + Object.keys(images).reduce(function (s, k) { return s + images[k]; }, 0);
  }

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

  function containerList(n) {
    return Html.t`<div class="cont-list" role="group" aria-labelledby="graphContTitle">${n.containers.map(containerItem)}</div>`;
  }

  function containerNotes(n) {
    const list = n.containers;
    const orDash = function (v, unit) { return Fmt.orDash(v, function (x) { return Fmt.num(x, 2) + ' ' + unit; }); };
    return Html.t`<p class="muted small">${I18n.t('graph.cPeaks', {cpu: Fmt.num(total(list, 'peakCpu'), 2) + ' vCPU', mem: Fmt.num(total(list, 'peakMemGb'), 2) + ' GB',
      tcpu: orDash(n.peakCpu, 'vCPU'), tmem: orDash(n.peakMemGb, 'GB')})}</p><p class="muted small">${I18n.t('graph.cStorageTotal', {gb: gb(tjobStorage(list)) + ' GB'})}</p>`;
  }

  function containerSection(n) {
    const list = n.containers || [];
    const title = Html.t`<h4 id="graphContTitle">${I18n.t('graph.containers')}</h4>`;
    if (!list.length) { return Html.t`${title}<p class="muted">${I18n.t('graph.noContainers')}</p>`; }
    return Html.t`${title}${containerNotes(n)}${containerList(n)}`;
  }

  /* Always visible, right after the tests executed and before the measurements. */
  function containerChartSection(n) {
    if (!(n.containers || []).length) { return ''; }
    const picks = METRICS.map(function (m) {
      return Html.t`<label><input type="radio" name="contMetric" value="${m.id}"${m.id === metricId ? Html.raw(' checked') : ''}><span>${I18n.t(m.label)}</span></label>`;
    });
    return Html.t`<div id="graphContChartBox"><h4>${I18n.t('graph.cChart')}</h4><div class="metric-pick" role="radiogroup" aria-label="${I18n.t('graph.mLabel')}">${picks}</div><div class="chart cont-chart" id="graphContChart" role="img"></div></div>`;
  }

  /* One bar group per container for the chosen resource: storage stacked (layer + image + volumes), memory and CPU as peak / average.
     The list above holds the same figures. */
  function renderContainersChart(node) {
    const box = document.getElementById('graphContChartBox');
    if (!node || !box) { return; } // nodes without containers have no chart
    const list = node.containers;
    const f = Charts.fonts('graphContChart');
    const m = METRICS.filter(function (x) { return x.id === metricId; })[0];
    const key = m.series[0][1];
    const largest = list.reduce(function (best, c) { return c[key] > best[key] ? c : best; }, list[0]);
    const at = function (c) { return m.stacked ? c.storageGb : c[key]; };
    const top = m.stacked ? gb(at(largest)) + ' GB' : Fmt.num(largest[key], 3) + ' ' + m.unit;
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

  /* node: graph node or null; alt: the selected alternative or null. */
  function renderPanel(node, alt, label) {
    const panel = document.getElementById('graphPanel');
    if (!node) {
      panel.innerHTML = '';
      return;
    }
    const items = alt ? alt.items.filter(function (i) { return i.tjob === node.id; }) : [];
    Html.set(panel, Html.t`<div class="panel-head"><h3 id="graphPanelTitle" tabindex="-1">${node.id}</h3><button type="button" id="graphClose" class="icon" data-i18n-title="graph.close">×</button></div><div class="muted">${
      I18n.t('graph.stage', {n: node.stage})}</div>${measured(node) ? '' : Html.t`<div class="warn small">${I18n.t('graph.noData')}</div>`}${
      node.predecessors.length ? Html.t`<h4>${I18n.t('graph.predecessors')}</h4><div>${chips(node.predecessors)}</div>` : ''}<h4>${I18n.t('graph.resources')}</h4><div>${
      chips(node.resources)}</div><h4 id="graphTestsTitle">${I18n.t('graph.tests') + ' (' + node.tests.length + ')'}</h4>${testList(node)}${containerChartSection(node)}<h4>${
      I18n.t('graph.measures')}</h4>${measures(node)}${containerSection(node)}<h4>${I18n.t('graph.cost', {alt: label || ''})}</h4>${costRows(items, I18n.t('graph.cost', {alt: label || ''}))}`);
    I18n.apply();
    const box = document.getElementById('graphContChartBox');
    if (box) {
      renderContainersChart(node);
      box.addEventListener('change', function (e) {
        if (e.target.name === 'contMetric') { metricId = e.target.value; renderContainersChart(node); }
      });
    }
  }

  /* Seconds the execution graph spans (used to give the usage chart the same time axis). */
  function span(graph) { return layout(graph.nodes).longest; }

  return {span: span, render: render, renderPanel: renderPanel, renderContainersChart: renderContainersChart, renderList: renderList, focusItem: focusItem, focusPanel: focusPanel};
})();

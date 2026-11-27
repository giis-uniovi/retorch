/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Execution graph drawn as a Gantt chart (bar length = TJob duration, three segments = setup / tests / teardown),
   dependency arrows between bars, the keyboard-accessible list of TJobs and the side panel with the details of the selected TJob. */
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

  function edges(lay) {
    const data = [];
    lay.rows.forEach(function (r) {
      r.node.predecessors.forEach(function (p) {
        if (lay.byId[p]) { data.push({coords: [[lay.byId[p].end, lay.byId[p].row], [r.start, r.row]]}); }
      });
    });
    return {type: 'lines', coordinateSystem: 'cartesian2d', silent: true, z: 5, data: data, symbol: ['none', 'arrow'], symbolSize: 7,
      lineStyle: {color: Theme.colors().muted, width: 1.2, curveness: 0.2, opacity: 0.85}};
  }

  function tooltip(lay) {
    return function (p) {
      if (p.seriesType !== 'custom') { return ''; }
      const r = lay.byId[p.data[5]];
      const lines = ['<b>' + Fmt.esc(r.node.id) + '</b> · ' + I18n.t('graph.stage', {n: r.node.stage})];
      if (r.noData) { return lines.concat(['<i>' + I18n.t('graph.noData') + '</i>']).join('<br>'); }
      return lines.concat([
        I18n.t('graph.start') + ': ' + Fmt.dur(r.start), I18n.t('graph.setup') + ': ' + Fmt.dur(r.setup),
        I18n.t('graph.exec') + ': ' + Fmt.dur(r.exec), I18n.t('graph.teardown') + ': ' + Fmt.dur(r.tear),
        '<b>' + I18n.t('graph.duration') + ': ' + Fmt.dur(r.end - r.start) + '</b>']).join('<br>');
    };
  }

  function describeChart(lay) {
    const stages = new Set(lay.rows.map(function (r) { return r.node.stage; })).size;
    Charts.describe('graphChart', I18n.t('graph.title') + '. ' + I18n.t('desc.graph', {n: lay.rows.length, stages: stages, dur: Fmt.dur(lay.longest)}));
  }

  let selectCallback = null;

  /* graph: {nodes}; selectedId: id or null; onSelect(id|null) is called when the user clicks a bar / the background. */
  function render(graph, selectedId, onSelect) {
    const lay = layout(graph.nodes);
    const f = Charts.fonts('graphChart');
    Charts.setHeight('graphChart', lay.rows.length * Math.round(f.root * 1.9) + f.root * 7);
    const c = Theme.colors();
    const chart = Charts.mk('graphChart', {
      aria: Charts.ARIA_DECAL,
      tooltip: {trigger: 'item', formatter: tooltip(lay)},
      legend: Charts.legend(f, {top: 0, selectedMode: false, data: SEGMENTS.map(function (s) { return I18n.t(s[0]); })}),
      grid: {left: 10, right: f.root * 1.25, top: f.root * 2.4, bottom: f.root * 4, containLabel: true},
      xAxis: Charts.axis(f, {type: 'value', min: 0, name: I18n.t('graph.time'), nameLocation: 'middle', nameGap: f.root * 1.8,
        axisLabel: {color: c.muted, fontSize: f.text, hideOverlap: true, formatter: function (v) { return Fmt.dur(v); }}}),
      yAxis: Charts.axis(f, {type: 'category', inverse: true, data: lay.rows.map(function (r) { return r.node.id; }), splitLine: {show: false}}),
      dataZoom: [{type: 'inside', xAxisIndex: 0, filterMode: 'none', zoomOnMouseWheel: 'ctrl', moveOnMouseWheel: false, preventDefaultMouseMove: false},
        {type: 'slider', xAxisIndex: 0, height: f.root * 0.9, bottom: f.root * 0.25, filterMode: 'none', borderColor: c.line, textStyle: {color: c.muted, fontSize: f.text}}],
      series: legendSeries(lay.rows, f).concat([
        {type: 'custom', name: 'tjobs', renderItem: bar(selectedId), encode: {x: [1, 4], y: 0}, z: 4,
          data: lay.rows.map(value)},
        edges(lay)])
    });
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
      list.innerHTML = nodes.map(function (n) {
        return '<li><button type="button" data-node="' + Fmt.esc(n.id) + '">' + Fmt.esc(itemText(n)) + '</button></li>';
      }).join('');
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
    return '<dt>' + I18n.t(labelKey) + '</dt><dd>' + value + '</dd>';
  }

  function costRows(items, caption) {
    const total = items.reduce(function (s, i) { return s + i.cost; }, 0);
    if (!total) { return '<p class="muted">' + I18n.t('graph.noCost') + '</p>'; }
    const rows = Fmt.PHASES.map(function (phase) {
      const sum = items.filter(function (i) { return i.phase === phase; }).reduce(function (s, i) { return s + i.cost; }, 0);
      return sum > 0 ? '<tr><th scope="row"><span class="dot" aria-hidden="true" style="background:' + Fmt.PHASE_COLORS[phase] + '"></span>' +
        I18n.t('phase.' + phase) + '</th><td>' + Fmt.usd(sum) + '</td></tr>' : '';
    }).join('');
    return '<table class="mini"><caption class="vh">' + Fmt.esc(caption) + '</caption><tbody>' + rows +
      '<tr class="total"><th scope="row">' + I18n.t('graph.total') + '</th><td>' + Fmt.usd(total) + '</td></tr></tbody></table>';
  }

  function measures(n) {
    const parts = [n.setupSec, n.execSec, n.teardownSec].filter(isNum);
    const total = parts.length ? parts.reduce(function (s, v) { return s + v; }, 0) : null;
    return '<dl class="measures">' + row('graph.start', Fmt.orDash(n.startSec, Fmt.dur)) + row('graph.setup', Fmt.orDash(n.setupSec, Fmt.dur)) +
      row('graph.exec', Fmt.orDash(n.execSec, Fmt.dur)) + row('graph.teardown', Fmt.orDash(n.teardownSec, Fmt.dur)) +
      row('graph.duration', Fmt.orDash(total, Fmt.dur)) +
      row('graph.peakCpu', Fmt.orDash(n.peakCpu, function (v) { return Fmt.num(v, 2) + ' vCPU'; })) +
      row('graph.peakMem', Fmt.orDash(n.peakMemGb, function (v) { return Fmt.num(v, 2) + ' GB'; })) + '</dl>';
  }

  function testList(n) {
    if (!n.tests.length) { return '<p class="muted">' + I18n.t('graph.noTests') + '</p>'; }
    return '<div class="tests-scroll" role="region" tabindex="0" aria-labelledby="graphTestsTitle"><ul class="tests">' +
      n.tests.map(function (t) { return '<li>' + Fmt.esc(t) + '</li>'; }).join('') + '</ul></div>';
  }

  function chips(list) {
    return list.length ? list.map(function (r) { return '<span class="chip">' + Fmt.esc(r) + '</span>'; }).join(' ') : '–';
  }

  /* ---- resources of the TJob: one row (card on narrow panels) per container, plus the storage split chart ---- */
  const CONTAINER_COLS = [['graph.cCpuPeak', 'peakCpu', 'vCPU'], ['graph.cCpuAvg', 'avgCpu', 'vCPU'], ['graph.cMemPeak', 'peakMemGb', 'GB'],
    ['graph.cMemAvg', 'avgMemGb', 'GB'], ['graph.layer', 'layerGb', 'GB'], ['graph.imageSize', 'imageGb', 'GB'], ['graph.volumes', 'volumeGb', 'GB'],
    ['graph.cStorage', 'storageGb', 'GB']];
  const STORAGE_STACKS = [['graph.layer', 'layerGb', 0], ['graph.imageSize', 'imageGb', 3], ['graph.volumes', 'volumeGb', 2]];

  const gb = function (v) { return Fmt.num(v, v > 0 && v < 0.1 ? 3 : 2); };
  const total = function (list, key) { return list.reduce(function (s, c) { return s + c[key]; }, 0); };

  /* Writable layers + volumes + images; an image shared by several containers counts once. */
  function tjobStorage(list) {
    const images = {};
    list.forEach(function (c, i) { images[c.image ? c.image : '#' + i] = c.imageGb; });
    return total(list, 'layerGb') + total(list, 'volumeGb') + Object.keys(images).reduce(function (s, k) { return s + images[k]; }, 0);
  }

  function containerCell(col, c) {
    const text = col[2] === 'vCPU' ? Fmt.num(c[col[1]], 2) : gb(c[col[1]]);
    return '<td class="num" data-label="' + Fmt.esc(I18n.t(col[0]) + ' (' + col[2] + ')') + '">' + text + '</td>';
  }

  function containerTable(n) {
    const head = '<thead><tr><th scope="col">' + I18n.t('graph.cName') + '</th>' + CONTAINER_COLS.map(function (c) {
      return '<th scope="col" class="num">' + Fmt.esc(I18n.t(c[0]) + ' (' + c[2] + ')') + '</th>';
    }).join('') + '</tr></thead>';
    const rows = n.containers.map(function (c) {
      return '<tr><th scope="row"><span class="name">' + Fmt.esc(c.name) + '</span>' +
        (c.image ? '<span class="sub-name"><span class="vh">' + I18n.t('graph.cImage') + ': </span>' + Fmt.esc(c.image) + '</span>' : '') + '</th>' +
        CONTAINER_COLS.map(function (col) { return containerCell(col, c); }).join('') + '</tr>';
    }).join('');
    return '<div class="rt-wrap" role="region" tabindex="0" aria-labelledby="graphContTitle"><table class="rt cont"><caption class="vh">' +
      Fmt.esc(I18n.t('graph.cCaption', {id: n.id})) + '</caption>' + head + '<tbody>' + rows + '</tbody></table></div>';
  }

  function containerNotes(n) {
    const list = n.containers;
    const orDash = function (v, unit) { return Fmt.orDash(v, function (x) { return Fmt.num(x, 2) + ' ' + unit; }); };
    return '<p class="muted small">' + Fmt.esc(I18n.t('graph.cPeaks', {cpu: Fmt.num(total(list, 'peakCpu'), 2) + ' vCPU', mem: Fmt.num(total(list, 'peakMemGb'), 2) + ' GB',
      tcpu: orDash(n.peakCpu, 'vCPU'), tmem: orDash(n.peakMemGb, 'GB')})) + '</p>' +
      '<p class="muted small">' + Fmt.esc(I18n.t('graph.cStorageTotal', {gb: gb(tjobStorage(list)) + ' GB'})) + '</p>';
  }

  function containerSection(n) {
    const list = n.containers || [];
    const title = '<h4 id="graphContTitle">' + I18n.t('graph.containers') + '</h4>';
    if (!list.length) { return title + '<p class="muted">' + I18n.t('graph.noContainers') + '</p>'; }
    const chart = total(list, 'storageGb') > 0
      ? '<p class="cont-title" id="graphContChartTitle">' + I18n.t('graph.cChart') + '</p><div class="chart cont-chart" id="graphContChart" role="img"></div>' : '';
    return title + containerNotes(n) + containerTable(n) + chart;
  }

  /* Stacked horizontal bars: writable layer + image + volumes per container. The table above holds the same figures. */
  function renderContainersChart(node) {
    if (!node || !document.getElementById('graphContChart')) { return; }
    const list = node.containers;
    const f = Charts.fonts('graphContChart');
    const largest = list.reduce(function (best, c) { return c.storageGb > best.storageGb ? c : best; }, list[0]);
    Charts.setHeight('graphContChart', Math.max(f.root * 11, list.length * f.root * 2.4 + f.root * 7));
    Charts.stacked('graphContChart', {
      categories: list.map(function (c) { return c.name; }), horizontal: true, axisName: 'GB', format: function (v) { return Fmt.num(v, 3) + ' GB'; },
      stacks: STORAGE_STACKS.map(function (s, n) {
        return {name: I18n.t(s[0]), color: Fmt.cycleColor(s[2]), pattern: Charts.cyclePattern(n), values: list.map(function (c) { return c[s[1]]; })};
      }),
      description: I18n.t('graph.cChart') + '. ' + I18n.t('desc.containers', {n: list.length, name: largest.name, gb: gb(largest.storageGb) + ' GB'})
    });
  }

  /* node: graph node or null; alt: the selected alternative or null. */
  function renderPanel(node, alt, label) {
    const panel = document.getElementById('graphPanel');
    if (!node) {
      panel.innerHTML = '<p class="muted">' + I18n.t('graph.hint') + '</p>';
      return;
    }
    const items = alt ? alt.items.filter(function (i) { return i.tjob === node.id; }) : [];
    panel.innerHTML =
      '<div class="panel-head"><h3 id="graphPanelTitle" tabindex="-1">' + Fmt.esc(node.id) + '</h3>' +
      '<button type="button" id="graphClose" class="icon" data-i18n-title="graph.close">×</button></div>' +
      '<div class="muted">' + I18n.t('graph.stage', {n: node.stage}) + '</div>' +
      (measured(node) ? '' : '<div class="warn small">' + I18n.t('graph.noData') + '</div>') +
      (node.predecessors.length ? '<h4>' + I18n.t('graph.predecessors') + '</h4><div>' + chips(node.predecessors) + '</div>' : '') +
      '<h4>' + I18n.t('graph.resources') + '</h4><div>' + chips(node.resources) + '</div>' +
      '<h4 id="graphTestsTitle">' + I18n.t('graph.tests') + ' (' + node.tests.length + ')</h4>' + testList(node) +
      '<h4>' + I18n.t('graph.measures') + '</h4>' + measures(node) +
      containerSection(node) +
      '<h4>' + I18n.t('graph.cost', {alt: Fmt.esc(label || '')}) + '</h4>' + costRows(items, I18n.t('graph.cost', {alt: label || ''}));
    I18n.apply();
    renderContainersChart(node);
  }

  return {render: render, renderPanel: renderPanel, renderContainersChart: renderContainersChart, renderList: renderList, focusItem: focusItem, focusPanel: focusPanel};
})();

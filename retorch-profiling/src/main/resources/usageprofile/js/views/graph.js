/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the execution graph drawn as a Gantt chart (bar length = TJob duration, three segments = setup / tests / teardown) and the
   keyboard-accessible list of TJobs that selects exactly like clicking a bar. The dependencies are not drawn (the arrows cluttered
   the chart); the panel of the selected TJob (views/panel.js) lists the predecessors.
   vm: {layout: Profile.ganttLayout(), nodeId: selected TJob id or null, onSelect(id|null): called when a bar or the background is clicked}. */
const GraphView = (function () {
  const SEGMENTS = [['graph.setup', 'tjob-setup'], ['graph.exec', 'tjob-testexec'], ['graph.teardown', 'tjob-teardown']];
  let root = null;
  let onSelectBar = null;
  let listKey = '';

  function mount(rootEl) { root = rootEl; }

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
      const fill = function (i) { return Charts.pattern(Fmt.PHASE_PATTERNS[SEGMENTS[i][1]], c.phase[SEGMENTS[i][1]]); };
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
    const c = Theme.colors();
    return SEGMENTS.map(function (p, i) {
      const s = {name: I18n.t(p[0]), type: 'bar', data: [], color: c.phase[p[1]], itemStyle: {decal: Charts.decal(Fmt.PHASE_PATTERNS[p[1]])}};
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

  function renderChart(vm) {
    const lay = vm.layout;
    View.$(root, 'graphHint').hidden = !!vm.nodeId;
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
        {type: 'custom', name: 'tjobs', renderItem: bar(vm.nodeId), encode: {x: [1, 4], y: 0}, z: 4, data: lay.rows.map(value)}])
    });
    Charts.linkZoom('graphChart');
    describeChart(lay);
    onSelectBar = vm.onSelect;
    // chart.off('click') only removes the chart level handlers. Never call getZr().off('click') without the handler:
    // it also removes the internal handler ECharts uses to dispatch the series events, and clicks on a bar stop working.
    chart.off('click');
    chart.on('click', function (p) { if (p.seriesType === 'custom') { onSelectBar(p.data[5]); } });
    if (!chart.backgroundClickBound) {
      chart.backgroundClickBound = true;
      chart.getZr().on('click', function (e) { if (!e.target && onSelectBar) { onSelectBar(null); } });
    }
  }

  /* ---- keyboard / touch path: one button per TJob ---- */
  function itemText(n) {
    return n.id + ' · ' + I18n.t('graph.stage', {n: n.stage}) + ' · ' + Fmt.orDash(Profile.duration(n), Fmt.dur);
  }

  /* The buttons are rebuilt only when the TJobs or the language change, so the focus stays on the button the user is using. */
  function renderList(vm) {
    const list = View.$(root, 'tjobList');
    const nodes = vm.layout.rows.map(function (r) { return r.node; });
    const key = I18n.lang() + '|' + nodes.map(function (n) { return n.id; }).join(',');
    if (key !== listKey) {
      listKey = key;
      Html.set(list, nodes.map(function (n) {
        return Html.t`<li><button type="button" data-act="select-node" data-node="${n.id}">${itemText(n)}</button></li>`;
      }));
    }
    Array.prototype.forEach.call(list.querySelectorAll('button'), function (b) {
      if (b.getAttribute('data-node') === vm.nodeId) { b.setAttribute('aria-current', 'true'); } else { b.removeAttribute('aria-current'); }
    });
  }

  function render(vm) {
    root.hidden = false;
    renderChart(vm);
    renderList(vm);
  }

  function focusItem(id) {
    const target = Array.prototype.find.call(View.$(root, 'tjobList').querySelectorAll('button'), function (b) { return b.getAttribute('data-node') === id; });
    if (target) { target.focus(); }
  }

  function resize() { Charts.resize('graphChart'); }

  return {mount: mount, render: render, resize: resize, focusItem: focusItem, root: function () { return root; }};
})();

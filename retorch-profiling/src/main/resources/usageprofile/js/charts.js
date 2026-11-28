/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* ECharts option builders. Every function reads the theme colours, the language and the font size at call time, so the
   charts must be rendered again when any of them (or the container width) changes. */
const Charts = (function () {
  const instances = {};
  const ARIA_DECAL = {enabled: true, label: {enabled: false}, decal: {show: true}};
  const DECAL_COLOR = 'rgba(0,0,0,0.38)';
  const PATTERN_CYCLE = ['solid', 'stripes', 'dots', 'backStripes', 'horizontal', 'vertical'];

  /* ---- sizes: derived from the user's root font size and the container width (320 px .. 1280 px) ---- */
  function rootPx() { return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16; }

  function fluid(minRem, maxRem, width) {
    const k = Math.min(1, Math.max(0, (width - 320) / 960));
    return (minRem + (maxRem - minRem) * k) * rootPx();
  }

  /* Pixel sizes for the texts and spacing of the chart that lives in the element `id`. */
  function fonts(id) {
    const w = document.getElementById(id).clientWidth || window.innerWidth;
    const root = rootPx();
    return {w: w, root: root, text: Math.round(fluid(0.75, 0.875, w)), tip: Math.round(fluid(0.8125, 0.9375, w)), narrow: w < 30 * root};
  }

  /* ---- non-colour cues: the same pattern as ECharts decal (legend, bars) and as canvas pattern (custom Gantt bars) ---- */
  function decal(kind) {
    const stripes = {symbol: 'rect', symbolSize: 1, dashArrayX: [1, 0], dashArrayY: [2, 5], color: DECAL_COLOR};
    const table = {
      solid: {symbol: 'none'},
      stripes: Object.assign({rotation: Math.PI / 4}, stripes),
      backStripes: Object.assign({rotation: -Math.PI / 4}, stripes),
      horizontal: Object.assign({rotation: 0}, stripes),
      vertical: Object.assign({rotation: Math.PI / 2}, stripes),
      dots: {symbol: 'circle', symbolSize: 1, dashArrayX: [[8, 8], [0, 8, 8, 0]], dashArrayY: [6, 0], color: DECAL_COLOR}
    };
    return table[kind] || table.solid;
  }

  function cyclePattern(index) { return PATTERN_CYCLE[index % PATTERN_CYCLE.length]; }

  const patternCache = {};

  function strokeLines(ctx, lines) {
    lines.forEach(function (l) { ctx.moveTo(l[0], l[1]); ctx.lineTo(l[2], l[3]); });
  }

  function paintPattern(ctx, kind, size) {
    const lines = {
      stripes: [[0, size, size, 0], [-1, 1, 1, -1], [size - 1, size + 1, size + 1, size - 1]],
      backStripes: [[0, 0, size, size], [-1, size - 1, 1, size + 1], [size - 1, -1, size + 1, 1]],
      horizontal: [[0, size / 2, size, size / 2]],
      vertical: [[size / 2, 0, size / 2, size]]
    };
    ctx.beginPath();
    if (kind === 'dots') { ctx.arc(size / 2, size / 2, 1.5, 0, 2 * Math.PI); ctx.fill(); return; }
    strokeLines(ctx, lines[kind] || []);
    ctx.stroke();
  }

  /* Fill for zrender shapes: the colour with the pattern drawn over it (a plain colour for 'solid'). */
  function pattern(kind, color) {
    if (kind === 'solid' || !kind) { return color; }
    const key = kind + color;
    if (patternCache[key]) { return patternCache[key]; }
    const size = 8;
    const dpr = window.devicePixelRatio || 1;
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = size * dpr;
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, size, size);
    ctx.strokeStyle = DECAL_COLOR;
    ctx.fillStyle = DECAL_COLOR;
    ctx.lineWidth = 1.6;
    paintPattern(ctx, kind, size);
    patternCache[key] = {image: canvas, repeat: 'repeat', scaleX: 1 / dpr, scaleY: 1 / dpr};
    return patternCache[key];
  }

  /* ---- instances ---- */
  function themed(option, f) {
    const c = Theme.colors();
    const base = {
      backgroundColor: 'transparent', textStyle: {color: c.muted, fontSize: f.text}, animation: false,
      tooltip: {backgroundColor: c.card, borderColor: c.line, confine: true, textStyle: {color: c.fg, fontSize: f.tip}}
    };
    const merged = Object.assign(base, option);
    merged.tooltip = Object.assign({}, base.tooltip, option.tooltip);
    return merged;
  }

  /* Text alternative of a chart: role img + aria-label on its container. */
  function describe(id, text) {
    const node = document.getElementById(id);
    node.setAttribute('role', 'img');
    node.setAttribute('aria-label', text);
  }

  /* Hides the tooltip before disposing: a pending tooltip update on a disposed instance throws. */
  function discard(chart) {
    try { chart.dispatchAction({type: 'hideTip'}); } catch (e) { /* already disposed */ }
    chart.dispose();
  }

  /* A fresh ECharts instance per render: reusing one with setOption(option, true) leaves a stale tooltip that throws on
     the next hover, and it also drops every handler registered on the old instance. */
  function mk(id, option, description) {
    if (instances[id]) { discard(instances[id]); }
    const chart = instances[id] = echarts.init(document.getElementById(id));
    chart.setOption(themed(option, fonts(id)));
    if (description) { describe(id, description); }
    return chart;
  }

  /* ---- shared time axis: the usage chart and the execution graph always show the same time span in the same pixels, so
     what a TJob does (graph) can be read against the resources used at that moment (usage). Same range, same left / right
     margins and the same zoom window (zooming one chart zooms the other). ---- */
  const TIME_CHARTS = ['usage', 'graphChart'];
  const timeAxisState = {span: 0, start: 0, end: 100, busy: false};

  function setTimeSpan(seconds) { timeAxisState.span = Math.max(1, seconds); }

  /* Pixel margins of the plot area, identical for every chart on the time axis. */
  function timeFrame(f) {
    return {left: Math.min(f.w * 0.3, f.root * (f.narrow ? 8 : 13)), right: f.root * (f.narrow ? 2.6 : 3.6)};
  }

  function timeAxis(f, extra) {
    const c = Theme.colors();
    return axis(f, Object.assign({type: 'value', min: 0, max: timeAxisState.span, axisLabel: {color: c.muted, fontSize: f.text, hideOverlap: true, formatter: Fmt.dur}}, extra));
  }

  function timeZoom(f) {
    const c = Theme.colors();
    return [{type: 'inside', xAxisIndex: 0, start: timeAxisState.start, end: timeAxisState.end, filterMode: 'none', zoomOnMouseWheel: 'ctrl', moveOnMouseWheel: false, preventDefaultMouseMove: false},
      {type: 'slider', xAxisIndex: 0, start: timeAxisState.start, end: timeAxisState.end, height: f.root, bottom: f.root * 0.25, filterMode: 'none', borderColor: c.line, textStyle: {color: c.muted, fontSize: f.text}}];
  }

  /* Call after mk(): copies the zoom window of this chart to the other chart(s) on the time axis. */
  function linkZoom(id) {
    instances[id].on('datazoom', function (e) {
      const z = e.batch ? e.batch[0] : e;
      if (timeAxisState.busy || z.start === undefined) { return; }
      timeAxisState.start = z.start;
      timeAxisState.end = z.end;
      timeAxisState.busy = true;
      TIME_CHARTS.forEach(function (other) {
        if (other !== id && instances[other]) { instances[other].dispatchAction({type: 'dataZoom', start: z.start, end: z.end}); }
      });
      timeAxisState.busy = false;
    });
  }

  function resizeAll() {
    Object.keys(instances).forEach(function (id) { instances[id].resize(); });
  }

  /* Sets the container height first so that ECharts measures the final size. */
  function setHeight(id, px) {
    document.getElementById(id).style.height = px + 'px';
    if (instances[id]) { instances[id].resize(); }
  }

  /* Semantic table with the data of a chart, inside a keyboard-scrollable labelled region. rows: [[rowHeader, cell...]] */
  function dataTable(id, caption, head, rows) {
    const capId = id + 'Cap';
    const th = head.map(function (h) { return Html.t`<th scope="col">${h}</th>`; });
    const tr = rows.map(function (r) {
      return Html.t`<tr><th scope="row">${r[0]}</th>${r.slice(1).map(function (v) { return Html.t`<td>${v}</td>`; })}</tr>`;
    });
    Html.set(document.getElementById(id), Html.t`<div class="data-scroll" role="region" tabindex="0" aria-labelledby="${capId}"><table><caption id="${capId}" class="vh">${
      caption}</caption><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table></div>`);
  }

  function axis(f, extra) {
    const c = Theme.colors();
    return Object.assign({
      splitNumber: f.narrow ? 3 : 5,
      axisLine: {lineStyle: {color: c.line}}, axisLabel: {color: c.muted, fontSize: f.text, hideOverlap: true},
      splitLine: {lineStyle: {color: c.line}}, nameTextStyle: {color: c.muted, fontSize: f.text}
    }, extra);
  }

  function legend(f, extra) {
    return Object.assign({textStyle: {color: Theme.colors().muted, fontSize: f.text}, type: 'scroll', itemWidth: f.root * 1.4,
      itemHeight: f.root * 0.9, itemGap: f.root * 0.75, pageIconColor: Theme.colors().muted}, extra);
  }

  /* ---- charts ---- */
  const stat = function (values) {
    const peak = values.length ? Math.max.apply(null, values) : 0;
    return {peak: peak, avg: values.length ? values.reduce(function (s, v) { return s + v; }, 0) / values.length : 0};
  };

  function usageSummary(timeline) {
    const rows = [['usage.cpu', timeline.cpu], ['usage.mem', timeline.memGb], ['usage.disk', timeline.diskGb]].map(function (r) {
      const s = stat(r[1]);
      return {name: I18n.t(r[0]), peak: s.peak, avg: s.avg};
    });
    const text = I18n.t('usage.title') + '. ' + I18n.t('desc.usage', {dur: Fmt.dur(timeline.t.length ? timeline.t[timeline.t.length - 1] : 0),
      cpu: Fmt.num(rows[0].peak, 2), mem: Fmt.num(rows[1].peak, 2), disk: Fmt.num(rows[2].peak, 2)});
    describe('usage', text);
    dataTable('usageData', I18n.t('usage.title'), [I18n.t('data.metric'), I18n.t('data.peak'), I18n.t('data.avg')],
      rows.map(function (r) { return [r.name, Fmt.num(r.peak, 2), Fmt.num(r.avg, 2)]; }));
  }

  function usage(timeline) {
    const colors = Fmt.SERIES_COLORS;
    const f = fonts('usage');
    const c = Theme.colors();
    const frame = timeFrame(f);
    const xy = function (values) { return values.map(function (v, i) { return [timeline.t[i], v]; }); };
    const line = function (key, nameKey, data, extra) {
      return Object.assign({name: I18n.t(nameKey), type: 'line', showSymbol: false, data: data, color: colors[key], lineStyle: {width: 2}}, extra);
    };
    const chart = mk('usage', {
      tooltip: {trigger: 'axis', valueFormatter: function (v) { return Fmt.num(v, 2); }}, legend: legend(f, {top: 0}),
      grid: {left: frame.left, right: frame.right, top: f.root * 3, bottom: f.root * 3.4},
      xAxis: timeAxis(f, {splitLine: {show: false}}),
      yAxis: [axis(f, {type: 'value', name: I18n.t('usage.axisLeft'), nameGap: f.root * 0.6, nameTextStyle: {color: c.muted, fontSize: f.text, align: 'left'}}),
        axis(f, {type: 'value', name: I18n.t('usage.axisRight'), splitLine: {show: false}, nameGap: f.root * 0.6, nameTextStyle: {color: c.muted, fontSize: f.text, align: 'right'}})],
      // The wheel zooms only with Ctrl and touch drags are not captured, so the chart never traps page scrolling.
      dataZoom: timeZoom(f),
      series: [
        line('cpu', 'usage.cpu', xy(timeline.cpu)), line('mem', 'usage.mem', xy(timeline.memGb), {lineStyle: {width: 2, type: 'dashed'}}),
        line('disk', 'usage.disk', xy(timeline.diskGb), {yAxisIndex: 1, lineStyle: {width: 2, type: 'dotted'}})]
    });
    linkZoom('usage');
    usageSummary(timeline);
    return chart;
  }

  /* rows: [{label, used, over}] already sorted and converted to the metric; opts: {showOver, pct, onClick(index)}. */
  function alternatives(rows, opts) {
    const f = fonts('compare');
    const formatter = opts.pct ? Fmt.pct : Fmt.usd;
    const series = [{name: I18n.t('alt.used'), type: 'bar', stack: 'cost', color: Fmt.SERIES_COLORS.used, itemStyle: {decal: decal('solid')},
      data: rows.map(function (r) { return r.used; })}];
    if (opts.showOver) {
      series.push({name: I18n.t('alt.over'), type: 'bar', stack: 'cost', color: Fmt.SERIES_COLORS.over,
        itemStyle: {decal: decal(Fmt.PHASE_PATTERNS.oversubscription)}, data: rows.map(function (r) { return r.over; })});
    }
    setHeight('compare', Math.max(f.root * 16, rows.length * f.root * 2.1 + f.root * 6));
    const totals = rows.map(function (r) { return r.used + r.over; });
    const chart = mk('compare', {
      aria: ARIA_DECAL,
      tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}, valueFormatter: formatter}, legend: legend(f, {top: 0}),
      grid: {left: 10, right: f.root * 1.5, top: f.root * 2.4, bottom: f.root * 2.2, containLabel: true},
      xAxis: axis(f, {type: 'value', name: I18n.t(opts.pct ? 'alt.axisPct' : 'alt.axisUsd'), nameLocation: 'middle', nameGap: f.root * 1.8}),
      yAxis: axis(f, {type: 'category', inverse: true, data: rows.map(function (r) { return r.label; }), splitLine: {show: false},
        axisLabel: {color: Theme.colors().muted, fontSize: f.text, width: Math.min(f.w * 0.45, f.root * 22), overflow: 'truncate'}}),
      series: series
    }, I18n.t('alt.title') + '. ' + I18n.t('desc.compare', {n: rows.length, min: formatter(Math.min.apply(null, totals.concat([0]))),
      max: formatter(Math.max.apply(null, totals.concat([0])))}));
    chart.off('click');
    chart.on('click', function (p) { opts.onClick(p.dataIndex); });
    return chart;
  }

  /* entries: [{name, value, color, pattern}]; title names the chart for assistive technologies. */
  function pie(id, entries, title) {
    const f = fonts(id);
    const total = entries.reduce(function (s, e) { return s + e.value; }, 0);
    const parts = entries.map(function (e) { return e.name + ' ' + Fmt.num(total ? 100 * e.value / total : 0, 0) + ' %'; }).join(', ');
    return mk(id, {
      aria: ARIA_DECAL,
      tooltip: {trigger: 'item', formatter: function (p) { return Html.str(Html.t`${p.name}: ${Fmt.usd(p.value)} (${Fmt.num(p.percent, 1)}%)`); }},
      legend: legend(f, {bottom: 0, type: 'plain'}),
      series: [{type: 'pie', radius: ['38%', '64%'], center: ['50%', '40%'], label: {show: false},
        itemStyle: {borderColor: Theme.colors().card, borderWidth: 1},
        data: entries.map(function (e) { return {name: e.name, value: e.value, itemStyle: {color: e.color, decal: decal(e.pattern)}}; })}]
    }, title + '. ' + I18n.t('desc.pie', {parts: parts}));
  }

  /* Stacked bars. spec: {categories:[label], stacks:[{name,color,pattern,values[]}], horizontal, description, axisName (default USD), format (default Fmt.usd)} */
  function stacked(id, spec) {
    const f = fonts(id);
    const valueAxis = axis(f, {type: 'value', name: spec.axisName || 'USD', nameLocation: spec.horizontal ? 'middle' : 'end', nameGap: f.root * 1.8});
    const categoryAxis = axis(f, {type: 'category', data: spec.categories, splitLine: {show: false}, inverse: !!spec.horizontal,
      axisLabel: {color: Theme.colors().muted, fontSize: f.text, width: Math.min(f.w * 0.45, f.root * 22), overflow: 'truncate', hideOverlap: !spec.horizontal}});
    return mk(id, {
      aria: ARIA_DECAL,
      tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}, valueFormatter: spec.format || Fmt.usd}, legend: legend(f, {top: 0}),
      grid: {left: 10, right: f.root * 1.25, top: f.root * (spec.horizontal ? 2.4 : 3.4), bottom: f.root * (spec.horizontal ? 2.2 : 0.6), containLabel: true},
      xAxis: spec.horizontal ? valueAxis : categoryAxis,
      yAxis: spec.horizontal ? categoryAxis : valueAxis,
      series: spec.stacks.map(function (s) {
        return {name: s.name, type: 'bar', stack: spec.grouped ? undefined : 'cost', color: s.color, itemStyle: {decal: decal(s.pattern)}, data: s.values};
      })
    }, spec.description);
  }

  return {setTimeSpan: setTimeSpan, timeFrame: timeFrame, timeAxis: timeAxis, timeZoom: timeZoom, linkZoom: linkZoom, mk: mk, resizeAll: resizeAll, setHeight: setHeight, axis: axis, legend: legend, fonts: fonts, describe: describe,
    dataTable: dataTable, ARIA_DECAL: ARIA_DECAL, decal: decal, pattern: pattern, cyclePattern: cyclePattern,
    usage: usage, alternatives: alternatives, pie: pie, stacked: stacked};
})();

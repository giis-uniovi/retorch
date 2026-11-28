/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Projection simulator view: the form, the results, the break-even table and the chart. The formulas are in model/simulation.js. Deep links: #runs=&days=&pattern=perRun|always|hours&hours=&disc=&buy=<termYears>-<payment>|od */
const Simulator = (function () {
  const DEFAULTS = Simulation.DEFAULTS;
  const LIMITS = Simulation.LIMITS;
  const PATTERNS = Simulation.PATTERNS;
  const FIELDS = {runs: 'simRuns', days: 'simDays', hours: 'simHours', disc: 'simDisc'};
  const MAX_CURVES = Simulation.MAX_CURVES;
  const LINE_STYLES = {perRun: 'solid', od: 'dashed', res: 'dotted'};
  const calc = Simulation.calc;
  const isVm = Simulation.isVm;
  const reservationKeys = Simulation.reservationKeys;

  const t = function (key, params) { return I18n.t(key, params); };
  const el = function (id) { return document.getElementById(id); };

  /* ---- state of the form ---- */
  let data = null;
  let helpers = null;
  let wantedBuy = DEFAULTS.buy;
  let current = null;
  let timer = null;
  let announceTimer = null;
  let lastAnnounced = '';

  function rangeText(key) {
    const digits = function (v) { return v % 1 ? 1 : 0; };
    return {min: Fmt.num(LIMITS[key][0], digits(LIMITS[key][0])), max: Fmt.num(LIMITS[key][1], digits(LIMITS[key][1]))};
  }

  /* Marks the field invalid (aria-invalid + message linked by aria-describedby) and returns the value clamped to its range. */
  function readNumber(key) {
    const input = el(FIELDS[key]);
    const err = el(FIELDS[key] + 'Err');
    const raw = parseFloat(input.value);
    const ok = isFinite(raw) && raw >= LIMITS[key][0] && raw <= LIMITS[key][1];
    input.setAttribute('aria-invalid', String(!ok));
    err.hidden = ok;
    err.textContent = ok ? '' : t('sim.err', rangeText(key));
    if (ok) { input.removeAttribute('aria-describedby'); } else { input.setAttribute('aria-describedby', err.id); }
    return isFinite(raw) ? Math.min(LIMITS[key][1], Math.max(LIMITS[key][0], raw)) : DEFAULTS[key];
  }

  function readForm() {
    const checked = document.querySelector('input[name="simPattern"]:checked');
    const pattern = checked ? checked.value : DEFAULTS.pattern;
    el('simHoursField').hidden = pattern !== 'hours';
    wantedBuy = el('simBuy').value || 'od';
    return {runs: readNumber('runs'), days: readNumber('days'), hours: readNumber('hours'), disc: readNumber('disc'),
      pattern: pattern, buy: wantedBuy};
  }

  /* ---- text helpers ---- */
  function termLabel(n) { return t(n === 1 ? 'sim.year' : 'sim.years', {n: n}); }

  function buyLabel(key) {
    if (key === 'od') { return t('sim.buy.od'); }
    const parts = key.split('-');
    return t('sim.buy.res', {term: termLabel(Number(parts[0])), pay: t('sim.pay.' + parts[1])});
  }

  function modeLabel(row, p) {
    const base = {
      perRun: function () { return t('sim.mode.perRun'); }, perUse: function () { return t('sim.mode.perUse'); },
      od24: function () { return t('sim.mode.od24'); }, odHours: function () { return t('sim.mode.odHours', {h: Fmt.num(p.hours, 1)}); },
      res: function () { return buyLabel(p.buy) + ', 24x7'; }
    }[row.mode]();
    return row.missing ? base + ' · ' + t('sim.mode.noRes') : base;
  }

  const utilText = function (row) {
    if (row.util === null) { return '–'; }
    return Html.t`${Fmt.num(row.util * 100, 0) + ' %'}${row.fits ? '' : Html.t` · <b class="nofit">${t('sim.nofit')}</b>`}`;
  };

  /* ---- results ---- */
  const cell = function (key, text, cls) {
    return Html.t`<td${cls ? Html.t` class="${cls}"` : ''} data-label="${t(key)}">${text}</td>`;
  };

  /* keys: header texts; the ones in `numeric` are right aligned like their cells. */
  const head = function (keys, numeric) {
    return Html.t`<thead><tr>${keys.map(function (k) {
      return Html.t`<th scope="col"${numeric.indexOf(k) >= 0 ? Html.raw(' class="num"') : ''}>${t(k)}</th>`;
    })}</tr></thead>`;
  };

  function resultsTable(model, p) {
    const rows = model.rows.map(function (r) {
      const isBest = r === model.best;
      return Html.t`<tr${isBest ? Html.raw(' class="best"') : ''}><th scope="row">${helpers.label(r.alt)}${isBest ? Html.t`<span class="tag">${t('sim.cheapest')}</span>` : ''}</th>${
        cell('sim.col.mode', modeLabel(r, p))}${cell('sim.col.month', Fmt.usd(r.monthly), 'num')}${cell('sim.col.year', Fmt.usd(r.monthly * 12), 'num')}${
        cell('sim.col.util', utilText(r), 'num')}</tr>`;
    });
    Html.set(el('simTable'), Html.t`<caption class="vh">${t('sim.cap')}</caption>${
      head(['sim.col.alt', 'sim.col.mode', 'sim.col.month', 'sim.col.year', 'sim.col.util'], ['sim.col.month', 'sim.col.year', 'sim.col.util'])}<tbody>${rows}</tbody>`);
  }

  function summaryText(model) {
    if (!model.best) { return ''; }
    return t('sim.summary', {alt: helpers.label(model.best.alt), month: Fmt.usd(model.best.monthly), year: Fmt.usd(model.best.monthly * 12)});
  }

  function notes(model, p, hasReservations) {
    const list = [];
    if (!hasReservations) { list.push(['note', t('sim.noRes')]); }
    if (hasReservations && p.pattern === 'perRun' && p.buy !== 'od') { list.push(['note', t('sim.resNotApplied')]); }
    if (p.pattern !== 'perRun' && model.rows.some(function (r) { return !isVm(r.alt); })) { list.push(['note', t('sim.containerNote')]); }
    const misfit = model.rows.filter(function (r) { return !r.fits; }).length;
    if (misfit) {
      list.push(['warn', t('sim.nofitWarn', {n: misfit, runs: Fmt.num(p.runs, 1), dur: Fmt.dur(data.durationSec),
        h: Fmt.num(calc.availableHours(p.pattern, p.hours), 1)})]);
    }
    return list.map(function (n) { return Html.t`<div class="${n[0]}">${n[1]}</div>`; });
  }

  const verdictText = function (row) {
    if (!row.priced) { return t('sim.be.none'); }
    return t({yes: 'sim.be.yes', not: 'sim.be.not', never: 'sim.be.never'}[row.verdict]);
  };

  function breakEvenTable(rows) {
    if (!rows.length) { Html.set(el('simBe'), ''); return; }
    const num = function (v, digits) { return isFinite(v) ? Fmt.num(v, digits) : '–'; };
    const body = rows.map(function (r) {
      return Html.t`<tr><th scope="row">${helpers.label(r.alt)}</th>${cell('sim.be.always', r.priced ? Fmt.usd(r.always) : '–', 'num')}${
        cell('sim.be.perRun', Fmt.usd(r.perRun), 'num')}${cell('sim.be.runs', r.priced ? num(r.runs, 1) : '–', 'num')}${
        cell('sim.be.max', num(r.max, 0), 'num')}${cell('sim.be.verdict', verdictText(r))}</tr>`;
    });
    Html.set(el('simBe'), Html.t`<caption class="vh">${t('sim.be.cap')}</caption>${
      head(['sim.col.alt', 'sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max', 'sim.be.verdict'],
        ['sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max'])}<tbody>${body}</tbody>`);
  }

  /* ---- chart: monthly cost vs runs per day ---- */
  /* Lines of one projected alternative with their names: [{kind, name, data: [[runs, usd]]}]. */
  function curves(row, p, xs) {
    return Simulation.curves(row, p, xs, data.durationSec).map(function (line) {
      const name = line.kind === 'perRun' ? t('sim.line.perRun')
        : line.kind === 'res' ? t('sim.line.res')
        : line.hours !== null ? t('sim.line.odHours', {h: Fmt.num(line.hours, 1)}) : t('sim.line.od24');
      return {kind: line.kind, name: name, data: line.data};
    });
  }

  function markLine(p, f) {
    const c = Theme.colors();
    return {silent: true, symbol: 'none', lineStyle: {color: c.fg, width: 1.5, type: 'solid'},
      label: {formatter: t('sim.chart.now', {n: Fmt.num(p.runs, 1)}), color: c.fg, fontSize: f.text, position: 'insideEndTop'}, data: [{xAxis: p.runs}]};
  }

  function chartSeries(shown, p, xs, f) {
    const series = [];
    shown.forEach(function (row, n) {
      curves(row, p, xs).forEach(function (line) {
        const s = {name: helpers.label(row.alt) + ' · ' + line.name, type: 'line', showSymbol: false, data: line.data, color: Fmt.cycleColor(n),
          lineStyle: {width: 2, type: LINE_STYLES[line.kind]}};
        if (!series.length) { s.markLine = markLine(p, f); }
        series.push(s);
      });
    });
    return series;
  }

  function renderChart() {
    if (!current || !el('simChart')) { return; }
    const p = current.p;
    const shown = current.rows.slice(0, MAX_CURVES);
    const xs = Simulation.range(Simulation.runAxis(p, calc.maxRuns(data.durationSec, Simulation.DAY_HOURS)));
    const f = Charts.fonts('simChart');
    const c = Theme.colors();
    Charts.setHeight('simChart', f.root * (f.narrow ? 24 : 22));
    Charts.mk('simChart', {
      tooltip: {trigger: 'axis', axisPointer: {type: 'line', snap: true}, valueFormatter: Fmt.usd}, legend: Charts.legend(f, {top: 0}),
      grid: {left: 10, right: f.root * 1.25, top: f.root * 3.4, bottom: f.root * 3, containLabel: true},
      xAxis: Charts.axis(f, {type: 'value', min: Math.min(1, p.runs), max: xs[xs.length - 1], name: t('sim.chart.x'), nameLocation: 'middle',
        nameGap: f.root * 1.8, splitLine: {show: false}, axisLabel: {color: c.muted, fontSize: f.text, hideOverlap: true}}),
      yAxis: Charts.axis(f, {type: 'value', name: t('sim.chart.y'), nameGap: f.root * 0.6, nameTextStyle: {color: c.muted, fontSize: f.text, align: 'left'},
        axisLabel: {color: c.muted, fontSize: f.text, formatter: function (v) { return v >= 10 ? '$' + Fmt.num(v, 0) : (v === 0 ? '$0' : Fmt.usd(v)); }}}),
      series: chartSeries(shown, p, xs, f)
    }, t('sim.chart.title') + '. ' + t('desc.sim', {n: shown.length, runs: Fmt.num(p.runs, 1)}));
  }

  /* ---- render ---- */
  function renderResults() {
    const model = current;
    el('simSummary').textContent = summaryText(model);
    Html.set(el('simNotes'), notes(model, model.p, model.hasReservations));
    resultsTable(model, model.p);
    el('simBeIntro').textContent = t('sim.be.intro', {buy: buyLabel(model.p.buy)});
    breakEvenTable(Simulation.breakEvenRows(data.alternatives, data.durationSec, model.p));
  }

  function recompute() {
    const p = readForm();
    current = Simulation.compute(data.alternatives, data.durationSec, p);
    current.p = p;
    current.hasReservations = reservationKeys(data.alternatives).length > 0;
    renderResults();
    renderChart();
  }

  /* Rebuilds the purchase options (the labels depend on the language) keeping the wanted one. */
  function renderOptions() {
    const keys = ['od'].concat(reservationKeys(data.alternatives));
    const select = el('simBuy');
    Html.set(select, keys.map(function (k) {
      return Html.t`<option value="${k}"${k === wantedBuy ? Html.raw(' selected') : ''}>${buyLabel(k)}</option>`;
    }));
    if (keys.indexOf(wantedBuy) < 0) { wantedBuy = 'od'; select.value = 'od'; }
  }

  function render() {
    renderOptions();
    recompute();
  }

  /* ---- deep links and live updates ---- */
  function writeHash(p) {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    [['runs', p.runs], ['days', p.days], ['pattern', p.pattern], ['hours', p.hours], ['disc', p.disc], ['buy', p.buy]].forEach(function (kv) {
      params.set(kv[0], String(kv[1]));
    });
    try { window.history.replaceState(null, '', '#' + params.toString()); } catch (e) { /* not allowed here: the link is just not updated */ }
  }

  /* Applies the simulator parameters of a hash (URLSearchParams); unknown or out of range values are ignored. */
  function applyParams(params) {
    Object.keys(FIELDS).forEach(function (key) {
      const v = parseFloat(params.get(key));
      if (isFinite(v) && v >= LIMITS[key][0] && v <= LIMITS[key][1]) { el(FIELDS[key]).value = String(v); }
    });
    const pattern = params.get('pattern');
    if (PATTERNS.indexOf(pattern) >= 0) { document.querySelector('input[name="simPattern"][value="' + pattern + '"]').checked = true; }
    if (params.get('buy')) { wantedBuy = params.get('buy'); }
  }

  function announceLater() {
    window.clearTimeout(announceTimer);
    announceTimer = window.setTimeout(function () {
      const text = summaryText(current);
      if (text && text !== lastAnnounced) { lastAnnounced = text; helpers.announce(text); }
    }, 900);
  }

  function onInput() {
    window.clearTimeout(timer);
    timer = window.setTimeout(function () {
      recompute();
      writeHash(current.p);
      announceLater();
    }, 120);
  }

  /* data: the page JSON; api: {label(alt), announce(text)}. */
  function init(pageData, api) {
    data = pageData;
    helpers = api;
    Object.keys(FIELDS).forEach(function (key) { el(FIELDS[key]).value = String(DEFAULTS[key]); });
    el('simForm').addEventListener('input', onInput);
    el('simForm').addEventListener('change', onInput);
    el('simForm').addEventListener('submit', function (e) { e.preventDefault(); });
  }

  return {init: init, render: render, renderChart: renderChart, applyParams: applyParams, state: function () { return current; }};
})();

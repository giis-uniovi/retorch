/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Projection simulator: how many times the suite runs per day, in which pattern, with which purchase option, and what it costs per
   month / year. The formulas are small pure functions (no DOM) exposed as Simulator.calc; the rest builds the form results, the
   break-even table and the chart. Deep links: #runs=&days=&pattern=perRun|always|hours&hours=&disc=&buy=<termYears>-<payment>|od */
const Simulator = (function () {
  const HOURS_MONTH = 730;
  const DAY_HOURS = 24;
  const SECONDS_HOUR = 3600;
  const DEFAULTS = {runs: 4, days: 22, pattern: 'perRun', hours: 10, disc: 0, buy: 'od'};
  const LIMITS = {runs: [0.1, 1000], days: [1, 31], hours: [0.5, 24], disc: [0, 95]};
  const PATTERNS = ['perRun', 'always', 'hours'];
  const FIELDS = {runs: 'simRuns', days: 'simDays', hours: 'simHours', disc: 'simDisc'};
  const PAY_ORDER = {NO_UPFRONT: 0, ALL_UPFRONT: 1};
  const MAX_CURVES = 4;
  const LINE_STYLES = {perRun: 'solid', od: 'dashed', res: 'dotted'};

  const t = function (key, params) { return I18n.t(key, params); };
  const el = function (id) { return document.getElementById(id); };

  /* ---- pure formulas (no DOM): every cost is USD, T = seconds of the measured execution ---- */
  const calc = (function () {
    const sumStorage = function (alt) {
      return alt.items.filter(function (i) { return i.object === 'storage'; }).reduce(function (s, i) { return s + i.cost; }, 0);
    };
    const storageRun = sumStorage;
    const computeRun = function (alt) { return Math.max(0, alt.total - sumStorage(alt)); };

    /* The disk exists all month (730 h) in the always-on patterns: its hourly rate is the measured storage cost over T. */
    function storageMonthly(storage, T) { return T > 0 ? storage / T * SECONDS_HOUR * HOURS_MONTH : 0; }

    function perRunCost(compute, storage, disc) { return compute * (1 - disc / 100) + storage; }

    function perRunMonthly(compute, storage, runs, days, disc) { return runs * days * perRunCost(compute, storage, disc); }

    function onDemandMonthly(hourly, disc, hoursOn, storage, T) { return hourly * (1 - disc / 100) * hoursOn + storageMonthly(storage, T); }

    function reservedMonthly(effectiveHourly, storage, T) { return effectiveHourly * HOURS_MONTH + storageMonthly(storage, T); }

    function hoursOn(pattern, hoursPerDay, days) { return pattern === 'always' ? HOURS_MONTH : hoursPerDay * days; }

    function availableHours(pattern, hoursPerDay) { return pattern === 'always' ? DAY_HOURS : hoursPerDay; }

    /* Fraction of the available hours per day that the executions keep the VM busy (> 1: the suite does not fit). */
    function utilisation(runs, T, available) { return available > 0 ? runs * T / SECONDS_HOUR / available : Infinity; }

    function maxRuns(T, available) { return T > 0 ? Math.floor(available * SECONDS_HOUR / T) : Infinity; }

    /* Runs per day from which the always-on option is cheaper than paying every execution on demand. */
    function breakEven(alwaysMonthly, days, perRun) { return perRun > 0 && days > 0 ? alwaysMonthly / (days * perRun) : Infinity; }

    return {HOURS_MONTH: HOURS_MONTH, storageRun: storageRun, computeRun: computeRun, storageMonthly: storageMonthly, perRunCost: perRunCost,
      perRunMonthly: perRunMonthly, onDemandMonthly: onDemandMonthly, reservedMonthly: reservedMonthly, hoursOn: hoursOn,
      availableHours: availableHours, utilisation: utilisation, maxRuns: maxRuns, breakEven: breakEven};
  })();

  /* ---- reservations ---- */
  const resKey = function (r) { return r.termYears + '-' + r.payment; };
  const isVm = function (a) { return a.kind === 'VM'; };

  function reservationKeys(alternatives) {
    const found = {};
    alternatives.forEach(function (a) { (a.reservations || []).forEach(function (r) { found[resKey(r)] = r; }); });
    return Object.keys(found).map(function (k) { return found[k]; }).sort(function (a, b) {
      return a.termYears - b.termYears || (PAY_ORDER[a.payment] || 0) - (PAY_ORDER[b.payment] || 0);
    }).map(resKey);
  }

  function findReservation(alt, key) {
    return (alt.reservations || []).find(function (r) { return resKey(r) === key; }) || null;
  }

  /* ---- projection of one alternative ---- */
  /* p: {runs, days, pattern, hours, disc, buy}; res: the reservation of this alternative for p.buy, or null. */
  function project(alt, T, p, res) {
    const storage = calc.storageRun(alt);
    const compute = calc.computeRun(alt);
    const unit = calc.perRunCost(compute, storage, p.disc);
    const row = {alt: alt, perRunCost: unit, res: res, util: null, fits: true, missing: false, mode: 'perRun',
      monthly: calc.perRunMonthly(compute, storage, p.runs, p.days, p.disc)};
    if (!isVm(alt)) { row.mode = p.pattern === 'perRun' ? 'perRun' : 'perUse'; return row; }
    if (p.pattern === 'perRun') { return row; }
    row.util = calc.utilisation(p.runs, T, calc.availableHours(p.pattern, p.hours));
    row.fits = row.util <= 1;
    if (res) {
      row.mode = 'res';
      row.monthly = calc.reservedMonthly(res.effectiveHourly, storage, T);
    } else {
      row.mode = p.pattern === 'always' ? 'od24' : 'odHours';
      row.missing = p.buy !== 'od';
      row.monthly = calc.onDemandMonthly(alt.hourlyPrice, p.disc, calc.hoursOn(p.pattern, p.hours, p.days), storage, T);
    }
    return row;
  }

  function compute(alternatives, T, p) {
    const rows = alternatives.map(function (a) { return project(a, T, p, p.buy === 'od' ? null : findReservation(a, p.buy)); });
    rows.sort(function (a, b) { return a.monthly - b.monthly; });
    const fitting = rows.filter(function (r) { return r.fits; });
    return {rows: rows, best: (fitting.length ? fitting : rows)[0] || null};
  }

  /* Always-on option (24x7) of a VM against the per-execution on-demand price. */
  function breakEvenRow(alt, T, p) {
    const storage = calc.storageRun(alt);
    const res = p.buy === 'od' ? null : findReservation(alt, p.buy);
    const row = {alt: alt, priced: p.buy === 'od' || !!res, max: calc.maxRuns(T, DAY_HOURS),
      perRun: calc.perRunCost(calc.computeRun(alt), storage, p.disc)};
    row.always = res ? calc.reservedMonthly(res.effectiveHourly, storage, T)
      : calc.onDemandMonthly(alt.hourlyPrice, p.disc, HOURS_MONTH, storage, T);
    row.runs = calc.breakEven(row.always, p.days, row.perRun);
    row.verdict = row.runs > row.max ? 'never' : (p.runs >= row.runs ? 'yes' : 'not');
    return row;
  }

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
    return Fmt.num(row.util * 100, 0) + ' %' + (row.fits ? '' : ' · <b class="nofit">' + t('sim.nofit') + '</b>');
  };

  /* ---- results ---- */
  const cell = function (key, text, cls) {
    return '<td' + (cls ? ' class="' + cls + '"' : '') + ' data-label="' + Fmt.esc(t(key)) + '">' + text + '</td>';
  };

  /* keys: header texts; the ones in `numeric` are right aligned like their cells. */
  const head = function (keys, numeric) {
    return '<thead><tr>' + keys.map(function (k) {
      return '<th scope="col"' + (numeric.indexOf(k) >= 0 ? ' class="num"' : '') + '>' + Fmt.esc(t(k)) + '</th>';
    }).join('') + '</tr></thead>';
  };

  function resultsTable(model, p) {
    const rows = model.rows.map(function (r) {
      const isBest = r === model.best;
      return '<tr' + (isBest ? ' class="best"' : '') + '><th scope="row">' + Fmt.esc(helpers.label(r.alt)) +
        (isBest ? '<span class="tag">' + t('sim.cheapest') + '</span>' : '') + '</th>' + cell('sim.col.mode', Fmt.esc(modeLabel(r, p))) +
        cell('sim.col.month', Fmt.usd(r.monthly), 'num') + cell('sim.col.year', Fmt.usd(r.monthly * 12), 'num') +
        cell('sim.col.util', utilText(r), 'num') + '</tr>';
    }).join('');
    el('simTable').innerHTML = '<caption class="vh">' + Fmt.esc(t('sim.cap')) + '</caption>' +
      head(['sim.col.alt', 'sim.col.mode', 'sim.col.month', 'sim.col.year', 'sim.col.util'], ['sim.col.month', 'sim.col.year', 'sim.col.util']) + '<tbody>' + rows + '</tbody>';
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
    return list.map(function (n) { return '<div class="' + n[0] + '">' + Fmt.esc(n[1]) + '</div>'; }).join('');
  }

  const verdictText = function (row) {
    if (!row.priced) { return t('sim.be.none'); }
    return t({yes: 'sim.be.yes', not: 'sim.be.not', never: 'sim.be.never'}[row.verdict]);
  };

  function breakEvenRows(p) {
    return data.alternatives.filter(isVm).map(function (a) { return breakEvenRow(a, data.durationSec, p); })
      .sort(function (a, b) { return a.always - b.always; });
  }

  function breakEvenTable(rows) {
    if (!rows.length) { el('simBe').innerHTML = ''; return; }
    const num = function (v, digits) { return isFinite(v) ? Fmt.num(v, digits) : '–'; };
    const body = rows.map(function (r) {
      return '<tr><th scope="row">' + Fmt.esc(helpers.label(r.alt)) + '</th>' +
        cell('sim.be.always', r.priced ? Fmt.usd(r.always) : '–', 'num') + cell('sim.be.perRun', Fmt.usd(r.perRun), 'num') +
        cell('sim.be.runs', r.priced ? num(r.runs, 1) : '–', 'num') + cell('sim.be.max', num(r.max, 0), 'num') +
        cell('sim.be.verdict', Fmt.esc(verdictText(r))) + '</tr>';
    }).join('');
    el('simBe').innerHTML = '<caption class="vh">' + Fmt.esc(t('sim.be.cap')) + '</caption>' +
      head(['sim.col.alt', 'sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max', 'sim.be.verdict'],
        ['sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max']) + '<tbody>' + body + '</tbody>';
  }

  /* ---- chart: monthly cost vs runs per day ---- */
  function runAxis(p, cap) {
    const top = Math.max(2, Math.min(100, cap));
    return Math.max(top, p.runs > top ? Math.min(1000, Math.ceil(p.runs * 1.2)) : top);
  }

  const range = function (n) { return Array.apply(null, new Array(n)).map(function (v, i) { return i + 1; }); };

  /* Lines of one alternative: [{kind, name, data: [[runs, usd]]}]. Always-on lines stop where the VM is full. */
  function curves(row, p, xs) {
    const T = data.durationSec;
    const storage = calc.storageRun(row.alt);
    const flat = function (value, cap) {
      return xs.filter(function (x) { return x <= cap; }).map(function (x) { return [x, value]; });
    };
    const lines = [{kind: 'perRun', name: t('sim.line.perRun'), data: xs.map(function (x) { return [x, x * p.days * row.perRunCost]; })}];
    if (!isVm(row.alt)) { return lines; }
    const hoursMode = p.pattern === 'hours';
    const hoursOn = hoursMode ? p.hours * p.days : HOURS_MONTH;
    const od = calc.onDemandMonthly(row.alt.hourlyPrice, p.disc, hoursOn, storage, T);
    lines.push({kind: 'od', name: hoursMode ? t('sim.line.odHours', {h: Fmt.num(p.hours, 1)}) : t('sim.line.od24'),
      data: flat(od, calc.maxRuns(T, hoursMode ? p.hours : DAY_HOURS))});
    if (row.res) {
      lines.push({kind: 'res', name: t('sim.line.res'), data: flat(calc.reservedMonthly(row.res.effectiveHourly, storage, T), calc.maxRuns(T, DAY_HOURS))});
    }
    return lines;
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
    const xs = range(runAxis(p, calc.maxRuns(data.durationSec, DAY_HOURS)));
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
    el('simNotes').innerHTML = notes(model, model.p, model.hasReservations);
    resultsTable(model, model.p);
    el('simBeIntro').textContent = t('sim.be.intro', {buy: buyLabel(model.p.buy)});
    breakEvenTable(breakEvenRows(model.p));
  }

  function recompute() {
    const p = readForm();
    current = compute(data.alternatives, data.durationSec, p);
    current.p = p;
    current.hasReservations = reservationKeys(data.alternatives).length > 0;
    renderResults();
    renderChart();
  }

  /* Rebuilds the purchase options (the labels depend on the language) keeping the wanted one. */
  function renderOptions() {
    const keys = ['od'].concat(reservationKeys(data.alternatives));
    const select = el('simBuy');
    select.innerHTML = keys.map(function (k) {
      return '<option value="' + Fmt.esc(k) + '"' + (k === wantedBuy ? ' selected' : '') + '>' + Fmt.esc(buyLabel(k)) + '</option>';
    }).join('');
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

  return {init: init, render: render, renderChart: renderChart, applyParams: applyParams, calc: calc, project: project, compute: compute,
    breakEvenRow: breakEvenRow, reservationKeys: reservationKeys, state: function () { return current; }};
})();

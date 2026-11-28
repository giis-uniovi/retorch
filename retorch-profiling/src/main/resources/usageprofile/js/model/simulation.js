/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Model: the projection simulator. How many times the suite runs per day, in which pattern and with which purchase option decide
   what every alternative costs per month. Pure functions (no DOM, no translations); every cost is USD and T is the duration in
   seconds of the measured execution. */
const Simulation = (function () {
  const HOURS_MONTH = 730;
  const DAY_HOURS = 24;
  const SECONDS_HOUR = 3600;
  const DEFAULTS = {runs: 4, days: 22, pattern: 'perRun', hours: 10, disc: 0, buy: 'od'};
  const LIMITS = {runs: [0.1, 1000], days: [1, 31], hours: [0.5, 24], disc: [0, 95]};
  const PATTERNS = ['perRun', 'always', 'hours'];
  const PAY_ORDER = {NO_UPFRONT: 0, ALL_UPFRONT: 1};
  const MAX_CURVES = 4;

  /* ---- formulas ---- */
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

  const calc = {HOURS_MONTH: HOURS_MONTH, storageRun: storageRun, computeRun: computeRun, storageMonthly: storageMonthly, perRunCost: perRunCost,
    perRunMonthly: perRunMonthly, onDemandMonthly: onDemandMonthly, reservedMonthly: reservedMonthly, hoursOn: hoursOn,
    availableHours: availableHours, utilisation: utilisation, maxRuns: maxRuns, breakEven: breakEven};

  /* ---- reservations ---- */
  const resKey = function (r) { return r.termYears + '-' + r.payment; };
  const isVm = function (a) { return a.kind === 'VM'; };

  /* The purchase options the alternatives offer, sorted by term and payment: ['1-NO_UPFRONT', '1-ALL_UPFRONT', ...]. */
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

  /* Projection of every alternative, cheapest per month first; best = the cheapest that fits (or the cheapest if none fits). */
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

  /* Break-even rows of the VM alternatives, cheapest always-on first. */
  function breakEvenRows(alternatives, T, p) {
    return alternatives.filter(isVm).map(function (a) { return breakEvenRow(a, T, p); }).sort(function (a, b) { return a.always - b.always; });
  }

  /* ---- chart: monthly cost against runs per day ---- */
  /* Last runs-per-day value of the x axis. */
  function runAxis(p, cap) {
    const top = Math.max(2, Math.min(100, cap));
    return Math.max(top, p.runs > top ? Math.min(1000, Math.ceil(p.runs * 1.2)) : top);
  }

  function range(n) { return Array.apply(null, new Array(n)).map(function (v, i) { return i + 1; }); }

  /* Lines of one projected alternative: [{kind: 'perRun'|'od'|'res', data: [[runs, usd]]}] (the od line also has `hours` in the
     'hours' pattern, so the view can name it). Always-on lines stop where the VM is full. */
  function curves(row, p, xs, T) {
    const storage = calc.storageRun(row.alt);
    const flat = function (value, cap) {
      return xs.filter(function (x) { return x <= cap; }).map(function (x) { return [x, value]; });
    };
    const lines = [{kind: 'perRun', data: xs.map(function (x) { return [x, x * p.days * row.perRunCost]; })}];
    if (!isVm(row.alt)) { return lines; }
    const hoursMode = p.pattern === 'hours';
    const on = hoursMode ? p.hours * p.days : HOURS_MONTH;
    const od = calc.onDemandMonthly(row.alt.hourlyPrice, p.disc, on, storage, T);
    lines.push({kind: 'od', hours: hoursMode ? p.hours : null, data: flat(od, calc.maxRuns(T, hoursMode ? p.hours : DAY_HOURS))});
    if (row.res) {
      lines.push({kind: 'res', data: flat(calc.reservedMonthly(row.res.effectiveHourly, storage, T), calc.maxRuns(T, DAY_HOURS))});
    }
    return lines;
  }

  /* Value inside the limits of its field, or the default when it is not a number. */
  function clamp(key, value) {
    const v = typeof value === 'number' ? value : parseFloat(value);
    return isFinite(v) ? Math.min(LIMITS[key][1], Math.max(LIMITS[key][0], v)) : DEFAULTS[key];
  }

  function inRange(key, value) {
    const v = typeof value === 'number' ? value : parseFloat(value);
    return isFinite(v) && v >= LIMITS[key][0] && v <= LIMITS[key][1];
  }

  return {HOURS_MONTH: HOURS_MONTH, DAY_HOURS: DAY_HOURS, DEFAULTS: DEFAULTS, LIMITS: LIMITS, PATTERNS: PATTERNS, MAX_CURVES: MAX_CURVES,
    calc: calc, project: project, compute: compute, breakEvenRow: breakEvenRow, breakEvenRows: breakEvenRows, reservationKeys: reservationKeys,
    findReservation: findReservation, runAxis: runAxis, range: range, curves: curves, clamp: clamp, inRange: inRange, isVm: isVm};
})();

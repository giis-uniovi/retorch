/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the projection simulator. The form (runs per day, days per month, pattern, hours, discount, purchase option), the projected
   cost of every alternative, the break-even table and the chart of monthly cost against runs per day. The formulas are in
   model/simulation.js. read() gives what the user typed (validated, marking the invalid fields) and render shows the state: an
   input the user left out of range keeps its text and its error while the state holds the clamped value.
   vm: {sim: {runs, days, pattern, hours, disc, buy}, model: Simulation.compute(), breakEven: Simulation.breakEvenRows(),
   hasReservations, buyKeys: ['od', ...], durationSec, labelOf(alt), chart: {xs, series: [{row, lines: Simulation.curves()}]}}. */
const SimulatorView = (function () {
  const FIELDS = {runs: 'simRuns', days: 'simDays', hours: 'simHours', disc: 'simDisc'};
  const LINE_STYLES = {perRun: 'solid', od: 'dashed', res: 'dotted'};
  const LIMITS = Simulation.LIMITS;
  const DEFAULTS = Simulation.DEFAULTS;
  const calc = Simulation.calc;
  const t = function (key, params) { return I18n.t(key, params); };

  let root = null;
  const el = function (id) { return View.$(root, id); };

  function mount(rootEl) { root = rootEl; }

  /* ---- the form ---- */
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
    return Simulation.clamp(key, raw);
  }

  /* What the form says: {runs, days, hours, disc, pattern, buy}, every number inside its limits. */
  function read() {
    const checked = root.querySelector('input[name="simPattern"]:checked');
    const pattern = checked ? checked.value : DEFAULTS.pattern;
    el('simHoursField').hidden = pattern !== 'hours';
    return {runs: readNumber('runs'), days: readNumber('days'), hours: readNumber('hours'), disc: readNumber('disc'),
      pattern: pattern, buy: el('simBuy').value || 'od'};
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

  /* The sentence with the cheapest option (also read aloud when the inputs change). */
  function summary(vm) {
    const best = vm.model.best;
    if (!best) { return ''; }
    return t('sim.summary', {alt: vm.labelOf(best.alt), month: Fmt.usd(best.monthly), year: Fmt.usd(best.monthly * 12)});
  }

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

  function resultsTable(vm) {
    const rows = vm.model.rows.map(function (r) {
      const isBest = r === vm.model.best;
      return Html.t`<tr${isBest ? Html.raw(' class="best"') : ''}><th scope="row">${vm.labelOf(r.alt)}${isBest ? Html.t`<span class="tag">${t('sim.cheapest')}</span>` : ''}</th>${
        cell('sim.col.mode', modeLabel(r, vm.sim))}${cell('sim.col.month', Fmt.usd(r.monthly), 'num')}${cell('sim.col.year', Fmt.usd(r.monthly * 12), 'num')}${
        cell('sim.col.util', utilText(r), 'num')}</tr>`;
    });
    Html.set(el('simTable'), Html.t`<caption class="vh">${t('sim.cap')}</caption>${
      head(['sim.col.alt', 'sim.col.mode', 'sim.col.month', 'sim.col.year', 'sim.col.util'], ['sim.col.month', 'sim.col.year', 'sim.col.util'])}<tbody>${rows}</tbody>`);
  }

  function notes(vm) {
    const p = vm.sim;
    const list = [];
    if (!vm.hasReservations) { list.push(['note', t('sim.noRes')]); }
    if (vm.hasReservations && p.pattern === 'perRun' && p.buy !== 'od') { list.push(['note', t('sim.resNotApplied')]); }
    if (p.pattern !== 'perRun' && vm.model.rows.some(function (r) { return !Simulation.isVm(r.alt); })) { list.push(['note', t('sim.containerNote')]); }
    const misfit = vm.model.rows.filter(function (r) { return !r.fits; }).length;
    if (misfit) {
      list.push(['warn', t('sim.nofitWarn', {n: misfit, runs: Fmt.num(p.runs, 1), dur: Fmt.dur(vm.durationSec),
        h: Fmt.num(calc.availableHours(p.pattern, p.hours), 1)})]);
    }
    return list.map(function (n) { return Html.t`<div class="${n[0]}">${n[1]}</div>`; });
  }

  const verdictText = function (row) {
    if (!row.priced) { return t('sim.be.none'); }
    return t({yes: 'sim.be.yes', not: 'sim.be.not', never: 'sim.be.never'}[row.verdict]);
  };

  function breakEvenTable(vm) {
    if (!vm.breakEven.length) { Html.set(el('simBe'), ''); return; }
    const num = function (v, digits) { return isFinite(v) ? Fmt.num(v, digits) : '–'; };
    const body = vm.breakEven.map(function (r) {
      return Html.t`<tr><th scope="row">${vm.labelOf(r.alt)}</th>${cell('sim.be.always', r.priced ? Fmt.usd(r.always) : '–', 'num')}${
        cell('sim.be.perRun', Fmt.usd(r.perRun), 'num')}${cell('sim.be.runs', r.priced ? num(r.runs, 1) : '–', 'num')}${
        cell('sim.be.max', num(r.max, 0), 'num')}${cell('sim.be.verdict', verdictText(r))}</tr>`;
    });
    Html.set(el('simBe'), Html.t`<caption class="vh">${t('sim.be.cap')}</caption>${
      head(['sim.col.alt', 'sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max', 'sim.be.verdict'],
        ['sim.be.always', 'sim.be.perRun', 'sim.be.runs', 'sim.be.max'])}<tbody>${body}</tbody>`);
  }

  /* ---- chart: monthly cost vs runs per day ---- */
  function lineName(line) {
    if (line.kind === 'perRun') { return t('sim.line.perRun'); }
    if (line.kind === 'res') { return t('sim.line.res'); }
    return line.hours !== null ? t('sim.line.odHours', {h: Fmt.num(line.hours, 1)}) : t('sim.line.od24');
  }

  function markLine(p, f) {
    const c = Theme.colors();
    return {silent: true, symbol: 'none', lineStyle: {color: c.fg, width: 1.5, type: 'solid'},
      label: {formatter: t('sim.chart.now', {n: Fmt.num(p.runs, 1)}), color: c.fg, fontSize: f.text, position: 'insideEndTop'}, data: [{xAxis: p.runs}]};
  }

  function chartSeries(vm, f) {
    const series = [];
    vm.chart.series.forEach(function (entry, n) {
      entry.lines.forEach(function (line) {
        const s = {name: vm.labelOf(entry.row.alt) + ' · ' + lineName(line), type: 'line', showSymbol: false, data: line.data, color: Fmt.cycleColor(n),
          lineStyle: {width: 2, type: LINE_STYLES[line.kind]}};
        if (!series.length) { s.markLine = markLine(vm.sim, f); }
        series.push(s);
      });
    });
    return series;
  }

  function renderChart(vm) {
    const p = vm.sim;
    const xs = vm.chart.xs;
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
      series: chartSeries(vm, f)
    }, t('sim.chart.title') + '. ' + t('desc.sim', {n: vm.chart.series.length, runs: Fmt.num(p.runs, 1)}));
  }

  /* ---- render ---- */
  /* Puts the state into the form: a number is written only when it differs from what the input says once clamped, so the text of an
     input the user left out of range (and its error) is kept. The purchase options are rebuilt only when their texts change. */
  function syncForm(vm) {
    Object.keys(FIELDS).forEach(function (key) {
      const input = el(FIELDS[key]);
      if (Simulation.clamp(key, input.value) !== vm.sim[key]) { input.value = String(vm.sim[key]); }
    });
    const radio = root.querySelector('input[name="simPattern"][value="' + vm.sim.pattern + '"]');
    if (radio) { radio.checked = true; }
    View.syncSelect(el('simBuy'), vm.buyKeys.map(function (k) { return [k, buyLabel(k)]; }), vm.sim.buy);
    read(); // marks the fields (and translates the errors) and shows the hours field when it applies
  }

  function renderResults(vm) {
    el('simSummary').textContent = summary(vm);
    Html.set(el('simNotes'), notes(vm));
    resultsTable(vm);
    el('simBeIntro').textContent = t('sim.be.intro', {buy: buyLabel(vm.sim.buy)});
    breakEvenTable(vm);
  }

  function render(vm) {
    syncForm(vm);
    renderResults(vm);
    renderChart(vm);
  }

  function resize() { Charts.resize('simChart'); }

  return {mount: mount, read: read, render: render, summary: summary, resize: resize};
})();

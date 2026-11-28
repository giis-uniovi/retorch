/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Locale-aware formatting helpers and the fill patterns that tell the phases apart without colour (the colours are in the CSS, see
   Theme.colors(); the lists of phases, kinds and objects come from the model). */
const Fmt = (function () {
  /* Second, non-colour cue for every phase: a fill pattern (see Charts.decal / Charts.pattern). */
  const PHASE_PATTERNS = {
    'coi-setup': 'dots', 'tjob-setup': 'stripes', 'tjob-testexec': 'solid',
    'tjob-teardown': 'backStripes', 'coi-teardown': 'horizontal', 'oversubscription': 'vertical'
  };

  function lang() { return I18n.lang(); }

  function num(v, digits) {
    return Number(v).toLocaleString(lang(), {minimumFractionDigits: digits, maximumFractionDigits: digits});
  }

  function usd(v) {
    return '$' + num(v, v >= 1 ? 2 : v >= 0.01 ? 3 : 5);
  }

  function pct(v) { return num(v, 0) + ' %'; }

  function dur(s) {
    if (s >= 3600) { return num(s / 3600, 1) + ' ' + I18n.t('unit.h'); }
    if (s >= 60) { return num(s / 60, 1) + ' ' + I18n.t('unit.min'); }
    return num(s, 0) + ' ' + I18n.t('unit.s');
  }

  function date(ms) {
    return ms ? new Date(ms).toLocaleString(lang()) : I18n.t('app.unknownDate');
  }

  /* Number with unit, or a dash when there is no measurement. */
  function orDash(v, formatter) {
    return v === null || v === undefined ? '–' : formatter(v);
  }


  /* Colour of the n-th item of a categorical series (TJobs, containers, simulator lines): the --cycle-* palette of the theme. */
  function cycleColor(index) {
    const cycle = Theme.colors().cycle;
    return cycle[index % cycle.length];
  }

  return {
    PHASE_PATTERNS: PHASE_PATTERNS,
    num: num, usd: usd, pct: pct, dur: dur, date: date, orDash: orDash, cycleColor: cycleColor
  };
})();

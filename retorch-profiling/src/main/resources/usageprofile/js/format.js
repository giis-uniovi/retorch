/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Constants (phases, colours) and locale-aware formatting helpers. */
const Fmt = (function () {
  const PHASES = ['coi-setup', 'tjob-setup', 'tjob-testexec', 'tjob-teardown', 'coi-teardown', 'oversubscription'];
  /* All the colours keep at least 3:1 against the white and the dark card (WCAG 1.4.11). */
  const PHASE_COLORS = {
    'coi-setup': '#64748b', 'tjob-setup': '#0284c7', 'tjob-testexec': '#16a34a',
    'tjob-teardown': '#d97706', 'coi-teardown': '#8b5cf6', 'oversubscription': '#dc2626'
  };
  /* Second, non-colour cue for every phase: a fill pattern (see Charts.decal / Charts.pattern). */
  const PHASE_PATTERNS = {
    'coi-setup': 'dots', 'tjob-setup': 'stripes', 'tjob-testexec': 'solid',
    'tjob-teardown': 'backStripes', 'coi-teardown': 'horizontal', 'oversubscription': 'vertical'
  };
  const OBJECT_COLORS = {compute: '#2563eb', storage: '#0f9488'};
  const SERIES_COLORS = {used: '#2563eb', over: '#dc2626', cpu: '#2563eb', mem: '#16a34a', disk: '#d97706'};
  /* Dark enough for white labels; also used for the TJob stacks and the graph stages. */
  const CYCLE_COLORS = ['#2563eb', '#0f766e', '#b45309', '#7c3aed', '#be185d', '#4d7c0f', '#0369a1', '#b91c1c', '#475569', '#a16207'];

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

  function esc(text) {
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function cycleColor(index) { return CYCLE_COLORS[index % CYCLE_COLORS.length]; }

  return {
    PHASES: PHASES, PHASE_COLORS: PHASE_COLORS, PHASE_PATTERNS: PHASE_PATTERNS, OBJECT_COLORS: OBJECT_COLORS, SERIES_COLORS: SERIES_COLORS,
    num: num, usd: usd, pct: pct, dur: dur, date: date, orDash: orDash, esc: esc, cycleColor: cycleColor
  };
})();

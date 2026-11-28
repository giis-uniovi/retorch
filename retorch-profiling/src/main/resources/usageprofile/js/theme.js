/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Light / dark / auto theme. The effective theme is always written to html[data-theme]; CSS variables do the rest, charts included:
   Theme.colors() reads the text colours and the chart palettes (--phase-*, --object-*, --series-*, --cycle-*) from the CSS custom
   properties in effect, so a theme can adjust them. */
const Theme = (function () {
  const MODES = ['auto', 'light', 'dark'];
  const STORE_KEY = 'retorch.theme';
  const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  /* The keys of the chart palettes: a CSS custom property --<palette>-<key> each. */
  const PALETTES = {
    phase: ['coi-setup', 'tjob-setup', 'tjob-testexec', 'tjob-teardown', 'coi-teardown', 'oversubscription'],
    object: ['compute', 'storage', 'browser'],
    series: ['used', 'over', 'cpu', 'mem', 'disk']
  };
  const CYCLE_SIZE = 10;
  let mode = 'auto';
  let onChange = function () {};
  let cache = null; // the colours of the theme in effect; dropped when the theme changes

  function effective() {
    if (mode === 'auto') { return media && media.matches ? 'dark' : 'light'; }
    return mode;
  }

  function applyToPage() {
    cache = null;
    document.documentElement.setAttribute('data-theme', effective());
  }

  /* remember: store the choice; silent: do not call the change callback (the caller renders). */
  function set(newMode, remember, silent) {
    mode = MODES.indexOf(newMode) >= 0 ? newMode : 'auto';
    if (remember) { try { window.localStorage.setItem(STORE_KEY, mode); } catch (e) { /* storage unavailable */ } }
    applyToPage();
    if (!silent) { onChange(); }
  }

  /* The colours in effect, for ECharts options: {fg, muted, line, card, accent, phase: {phase: colour}, object: {...}, series: {...},
     cycle: [colour]}. */
  function colors() {
    if (cache) { return cache; }
    const style = getComputedStyle(document.documentElement);
    const read = function (name) { return style.getPropertyValue(name).trim(); };
    const palette = function (name) {
      const out = {};
      PALETTES[name].forEach(function (key) { out[key] = read('--' + name + '-' + key); });
      return out;
    };
    const cycle = [];
    for (let i = 0; i < CYCLE_SIZE; i++) { cycle.push(read('--cycle-' + i)); }
    cache = {fg: read('--fg'), muted: read('--muted'), line: read('--line'), card: read('--card'), accent: read('--accent'),
      phase: palette('phase'), object: palette('object'), series: palette('series'), cycle: cycle};
    return cache;
  }

  function init(callback) {
    onChange = callback;
    let saved = null;
    try { saved = window.localStorage.getItem(STORE_KEY); } catch (e) { /* storage unavailable */ }
    mode = MODES.indexOf(saved) >= 0 ? saved : 'auto';
    applyToPage();
    if (media) {
      const follow = function () { if (mode === 'auto') { applyToPage(); onChange(); } };
      if (media.addEventListener) { media.addEventListener('change', follow); } else { media.addListener(follow); }
    }
  }

  return {MODES: MODES, init: init, set: set, mode: function () { return mode; }, effective: effective, colors: colors};
})();

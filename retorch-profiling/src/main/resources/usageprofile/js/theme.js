/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Light / dark / auto theme. The effective theme is always written to html[data-theme]; CSS variables do the rest. */
const Theme = (function () {
  const MODES = ['auto', 'light', 'dark'];
  const STORE_KEY = 'retorch.theme';
  const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  let mode = 'auto';
  let onChange = function () {};

  function effective() {
    if (mode === 'auto') { return media && media.matches ? 'dark' : 'light'; }
    return mode;
  }

  function applyToPage() {
    document.documentElement.setAttribute('data-theme', effective());
  }

  /* remember: store the choice; silent: do not call the change callback (the caller renders). */
  function set(newMode, remember, silent) {
    mode = MODES.indexOf(newMode) >= 0 ? newMode : 'auto';
    if (remember) { try { window.localStorage.setItem(STORE_KEY, mode); } catch (e) { /* storage unavailable */ } }
    applyToPage();
    if (!silent) { onChange(); }
  }

  /* Reads the colours currently in effect, for ECharts options. */
  function colors() {
    const style = getComputedStyle(document.documentElement);
    const read = function (name) { return style.getPropertyValue(name).trim(); };
    return {fg: read('--fg'), muted: read('--muted'), line: read('--line'), card: read('--card'), accent: read('--accent')};
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

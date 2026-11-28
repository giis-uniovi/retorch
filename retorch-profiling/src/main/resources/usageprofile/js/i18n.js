/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Translations (es, en, fr, de). The texts are not in this file: one JSON per language (resources/usageprofile/i18n/<lang>.json) that
   the generator inlines in <script type="application/json" id="i18n">. Keys are shared by every language (tests check it); a missing
   key falls back to English, and then to the key itself. */
const I18n = (function () {
  const LANGS = ['es', 'en', 'fr', 'de'];
  const NATIVE = {es: 'Español', en: 'English', fr: 'Français', de: 'Deutsch'};
  const STORE_KEY = 'retorch.lang';

  let TEXT = {}; // {lang: {key: text}}, read from the page (see load)
  let current = 'en';

  function detect() {
    const code = String(navigator.language || 'en').slice(0, 2).toLowerCase();
    return LANGS.indexOf(code) >= 0 ? code : 'en';
  }

  function stored() {
    try { return window.localStorage.getItem(STORE_KEY); } catch (e) { return null; }
  }

  /* texts: {lang: {key: text}}; the page loads them at start, tests load the JSON files. */
  function load(texts) { TEXT = texts || {}; }

  function loadFromPage() {
    const node = document.getElementById('i18n');
    if (!node) { return; }
    try { load(JSON.parse(node.textContent)); } catch (e) { /* the texts are missing: the keys are shown */ }
  }

  function t(key, params) {
    const table = TEXT[current] || TEXT.en || {};
    const english = TEXT.en || {};
    let text = table[key] !== undefined ? table[key] : (english[key] !== undefined ? english[key] : key);
    Object.keys(params || {}).forEach(function (name) { text = text.split('{' + name + '}').join(params[name]); });
    return text;
  }

  /* Updates every element declared with data-i18n / data-i18n-title and the document language and title. */
  function apply() {
    document.documentElement.lang = current;
    document.title = t('app.title');
    document.querySelectorAll('[data-i18n]').forEach(function (e) { e.textContent = t(e.getAttribute('data-i18n')); });
    document.querySelectorAll('[data-i18n-title]').forEach(function (e) {
      const text = t(e.getAttribute('data-i18n-title'));
      e.setAttribute('title', text);
      e.setAttribute('aria-label', text);
    });
    document.querySelectorAll('[data-i18n-alt]').forEach(function (e) { e.setAttribute('aria-label', t(e.getAttribute('data-i18n-alt'))); });
    document.querySelectorAll('[data-i18n-label]').forEach(function (e) { e.setAttribute('aria-label', t(e.getAttribute('data-i18n-label'))); });
    document.querySelectorAll('[data-i18n-tip]').forEach(function (e) { e.setAttribute('title', t(e.getAttribute('data-i18n-tip'))); });
  }

  function set(lang, remember) {
    current = LANGS.indexOf(lang) >= 0 ? lang : 'en';
    if (remember) { try { window.localStorage.setItem(STORE_KEY, current); } catch (e) { /* storage unavailable */ } }
  }

  function init() {
    loadFromPage();
    set(stored() || detect(), false);
  }

  return {LANGS: LANGS, NATIVE: NATIVE, t: t, lang: function () { return current; }, set: set, init: init, apply: apply, detect: detect, load: load};
})();

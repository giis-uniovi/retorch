/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* HTML built from templates: Html.t`<p>${value}</p>` escapes every interpolated value (text, translations and data alike), so
   a fragment is never concatenated by hand. Html.raw(text) marks a trusted fragment (already built with Html.t or static markup),
   arrays are joined, and null, undefined and false give nothing. */
const Html = (function () {
  /* A fragment that is already HTML: it is inserted as it is. */
  function Safe(text) { this.text = text; }
  Safe.prototype.toString = function () { return this.text; };

  const ENTITIES = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};

  function esc(value) {
    return String(value).replace(/[&<>"']/g, function (c) { return ENTITIES[c]; });
  }

  function raw(text) { return new Safe(String(text)); }

  /* One interpolated value as HTML text. */
  function part(value) {
    if (value instanceof Safe) { return value.text; }
    if (Array.isArray(value)) { return value.map(part).join(''); }
    return value === null || value === undefined || value === false ? '' : esc(value);
  }

  /* Tagged template: the static parts are trusted markup, the interpolated values are escaped. */
  function t(strings) {
    let out = strings[0];
    for (let i = 1; i < arguments.length; i++) { out += part(arguments[i]) + strings[i]; }
    return new Safe(out);
  }

  /* Fragments of a list joined with a separator (itself trusted markup, e.g. ' '). */
  function join(list, separator) {
    return new Safe(list.map(part).join(separator === undefined ? '' : separator));
  }

  /* Writes a fragment inside an element. */
  function set(element, fragment) {
    element.innerHTML = part(fragment);
    return element;
  }

  return {t: t, raw: raw, esc: esc, join: join, set: set, str: part};
})();

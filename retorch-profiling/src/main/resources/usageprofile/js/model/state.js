/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Model: the only mutable state of the page, one owner for what the user chose (language, theme, selected alternative and TJob,
   comparison, filters, chart options, simulator inputs). Values are plain data (strings, numbers, booleans, arrays): they are
   compared and serialised as JSON, so any state can be written to the URL and read back (model/hash.js).
   State.create(ctx) -> {get, defaults, update, subscribe}; update(patch) validates every value (invalid ones are ignored) and
   returns the changed keys. ctx (what is valid in this page): {langs, themes, altIds, nodeIds, providers, kinds, regions,
   cheapestIds, buyKeys, lang, defaultLang}. */
const State = (function () {
  const LANGS = ['es', 'en', 'fr', 'de'];
  const THEMES = ['auto', 'light', 'dark'];
  const ENUMS = {
    metric: ['usd', 'pct'], sort: ['cost', 'provider', 'over'], cmpGroup: ['phase', 'tjob'], group: ['phase', 'object'],
    contMetric: ['storage', 'mem', 'cpu']
  };
  const SIM_NUMBERS = ['runs', 'days', 'hours', 'disc'];

  const list = function (v) { return Array.isArray(v) ? v : []; };
  const has = function (values, v) { return list(values).indexOf(v) >= 0; };
  const sameJson = function (a, b) { return JSON.stringify(a) === JSON.stringify(b); };

  /* The state of a page without any choice of the user: the first (cheapest) alternative selected, the cheapest ones compared. */
  function defaults(ctx) {
    const alts = list(ctx.altIds);
    return {
      lang: ctx.defaultLang || 'en', theme: 'auto',
      selectedAlt: alts.length ? alts[0] : null, node: null,
      showOver: true, metric: 'usd', sort: 'cost', cmpGroup: 'phase', group: 'phase', contMetric: 'storage',
      compared: ordered(list(ctx.cheapestIds), ctx),
      filters: {offProviders: [], kind: '', region: ''},
      sim: {runs: Simulation.DEFAULTS.runs, days: Simulation.DEFAULTS.days, pattern: Simulation.DEFAULTS.pattern,
        hours: Simulation.DEFAULTS.hours, disc: Simulation.DEFAULTS.disc, buy: Simulation.DEFAULTS.buy}
    };
  }

  /* Ids without duplicates or unknown ones, in the order of the page (the order of the alternatives). */
  function ordered(ids, ctx) { return list(ctx.altIds).filter(function (id) { return has(ids, id); }); }

  /* Keeps only the valid part of a patch: unknown keys, unknown ids and out of range numbers are dropped. */
  function sanitize(patch, ctx) {
    const out = {};
    const p = patch || {};
    if (has(ctx.langs || LANGS, p.lang)) { out.lang = p.lang; }
    if (has(ctx.themes || THEMES, p.theme)) { out.theme = p.theme; }
    if (has(ctx.altIds, p.selectedAlt)) { out.selectedAlt = p.selectedAlt; }
    if (p.node === null || has(ctx.nodeIds, p.node)) { out.node = p.node; }
    if (typeof p.showOver === 'boolean') { out.showOver = p.showOver; }
    Object.keys(ENUMS).forEach(function (key) { if (has(ENUMS[key], p[key])) { out[key] = p[key]; } });
    if (Array.isArray(p.compared)) { out.compared = ordered(p.compared, ctx); }
    if (p.filters && typeof p.filters === 'object') { out.filters = sanitizeFilters(p.filters, ctx); }
    if (p.sim && typeof p.sim === 'object') { out.sim = sanitizeSim(p.sim, ctx); }
    return out;
  }

  function sanitizeFilters(f, ctx) {
    const out = {};
    if (Array.isArray(f.offProviders)) { out.offProviders = list(ctx.providers).filter(function (name) { return has(f.offProviders, name); }); }
    if (f.kind === '' || has(ctx.kinds, f.kind)) { out.kind = f.kind; }
    if (f.region === '' || has(ctx.regions, f.region)) { out.region = f.region; }
    return out;
  }

  function sanitizeSim(sim, ctx) {
    const out = {};
    SIM_NUMBERS.forEach(function (key) {
      if (sim[key] !== undefined && Simulation.inRange(key, sim[key])) { out[key] = Number(sim[key]); }
    });
    if (has(Simulation.PATTERNS, sim.pattern)) { out.pattern = sim.pattern; }
    if (sim.buy === 'od' || has(ctx.buyKeys, sim.buy)) { out.buy = sim.buy; }
    return out;
  }

  /* A complete patch for a state read from a link: what the link does not say is the default, except the keys in `keep`
     (language and theme of the user, which are not part of a navigation). */
  function resolve(patch, ctx, keep) {
    const base = defaults(ctx);
    const clean = sanitize(patch, ctx);
    const kept = sanitize(keep || {}, ctx);
    const out = Object.assign({}, base, kept, clean);
    out.filters = Object.assign({}, base.filters, clean.filters);
    out.sim = Object.assign({}, base.sim, clean.sim);
    return out;
  }

  function create(ctx) {
    let state = Object.assign(defaults(ctx), sanitize({lang: ctx.lang}, ctx));
    const subscribers = [];

    /* Applies the valid part of a patch (filters and sim merge key by key) and returns the top level keys that changed. */
    function update(patch) {
      const clean = sanitize(patch, ctx);
      const next = Object.assign({}, state);
      const changed = [];
      Object.keys(clean).forEach(function (key) {
        const value = key === 'filters' || key === 'sim' ? Object.assign({}, state[key], clean[key]) : clean[key];
        if (!sameJson(value, state[key])) { next[key] = value; changed.push(key); }
      });
      if (changed.length) {
        state = next;
        subscribers.slice().forEach(function (fn) { fn(changed, state); });
      }
      return changed;
    }

    return {
      get: function () { return state; },
      defaults: function () { return defaults(ctx); },
      update: update,
      /* fn(changedKeys, state) is called after every update that changes something; returns the function that unsubscribes. */
      subscribe: function (fn) {
        subscribers.push(fn);
        return function () { const at = subscribers.indexOf(fn); if (at >= 0) { subscribers.splice(at, 1); } };
      },
      ctx: ctx
    };
  }

  return {create: create, defaults: defaults, sanitize: sanitize, resolve: resolve, LANGS: LANGS, THEMES: THEMES, ENUMS: ENUMS};
})();

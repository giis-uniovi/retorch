/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Model: the deep links. The only place that knows the format of the URL hash, in both directions:
   parse(hash, ctx) -> patch of the state (only the keys the link has, validated) and serialize(state, ctx) -> hash text, so that
   parse(serialize(s)) gives s back for every valid state. A key is written only when it differs from the default state (the
   language of the browser, theme auto, the cheapest alternative selected and compared, ...). Keys of the link:
     lang=es|en|fr|de  theme=auto|light|dark  alt=<alternative id>  node=<TJob id>  over=0|1  metric=usd|pct
     sort=cost|provider|over  cmpgroup=phase|tjob  cmp=all|none|cheapest | cmpids=<id>,<id>... (',' and '%' escaped as %2C and %25 inside an id)
     group=phase|object  cont=storage|mem|cpu  noprov=<provider>,... (hidden providers)  kind=<VM|CONTAINER|BROWSER_SERVICE>  region=<region>
     runs=  days=  pattern=perRun|always|hours  hours=  disc=  buy=<termYears>-<payment>|od */
const Hash = (function () {
  const SIM_KEYS = ['runs', 'days', 'pattern', 'hours', 'disc', 'buy'];
  const SIMPLE = [['alt', 'selectedAlt'], ['node', 'node'], ['metric', 'metric'], ['sort', 'sort'], ['cmpgroup', 'cmpGroup'], ['group', 'group'],
    ['cont', 'contMetric']];

  /* Lists are comma separated: only '%' and ',' are escaped inside an id (the URL encoding of the whole hash does the rest). */
  const split = function (text) { return text ? text.split(',').map(decodeURIComponent) : []; };
  const join = function (ids) { return ids.map(function (id) { return id.replace(/%/g, '%25').replace(/,/g, '%2C'); }).join(','); };
  const same = function (a, b) { return JSON.stringify(a) === JSON.stringify(b); };

  function params(hash) {
    try { return new URLSearchParams(String(hash || '').replace(/^#/, '')); } catch (e) { return new URLSearchParams(''); }
  }

  /* Reads the keys of the link into a patch of the state (unknown or invalid values are dropped). */
  function parse(hash, ctx) {
    const p = params(hash);
    const patch = {};
    const get = function (key) { return p.has(key) ? p.get(key) : undefined; };
    if (get('lang') !== undefined) { patch.lang = get('lang'); }
    if (get('theme') !== undefined) { patch.theme = get('theme'); }
    SIMPLE.forEach(function (pair) { if (get(pair[0]) !== undefined) { patch[pair[1]] = get(pair[0]); } });
    if (get('over') !== undefined) { patch.showOver = get('over') !== '0'; }
    const cmp = get('cmp');
    if (cmp === 'all') { patch.compared = ctx.altIds; } else if (cmp === 'none') { patch.compared = []; } else if (cmp === 'cheapest') { patch.compared = ctx.cheapestIds; }
    if (get('cmpids') !== undefined) { try { patch.compared = split(get('cmpids')); } catch (e) { /* bad encoding: ignored */ } }
    const filters = {};
    if (get('noprov') !== undefined) { try { filters.offProviders = split(get('noprov')); } catch (e) { /* ignored */ } }
    if (get('kind') !== undefined) { filters.kind = get('kind'); }
    if (get('region') !== undefined) { filters.region = get('region'); }
    if (Object.keys(filters).length) { patch.filters = filters; }
    const sim = {};
    SIM_KEYS.forEach(function (key) {
      if (get(key) !== undefined) { sim[key] = key === 'pattern' || key === 'buy' ? get(key) : parseFloat(get(key)); }
    });
    if (Object.keys(sim).length) { patch.sim = sim; }
    return State.sanitize(patch, ctx);
  }

  /* The hash text (no leading #) of a state: only what differs from the default state. */
  function serialize(state, ctx) {
    const d = State.defaults(ctx);
    const out = new URLSearchParams();
    if (state.lang !== d.lang) { out.set('lang', state.lang); }
    if (state.theme !== d.theme) { out.set('theme', state.theme); }
    SIMPLE.forEach(function (pair) {
      const value = state[pair[1]];
      if (value !== null && value !== d[pair[1]]) { out.set(pair[0], value); }
    });
    if (state.showOver !== d.showOver) { out.set('over', state.showOver ? '1' : '0'); }
    if (!same(state.compared, d.compared)) {
      if (same(state.compared, ctx.altIds)) { out.set('cmp', 'all'); } else if (!state.compared.length) { out.set('cmp', 'none'); } else { out.set('cmpids', join(state.compared)); }
    }
    if (state.filters.offProviders.length) { out.set('noprov', join(state.filters.offProviders)); }
    if (state.filters.kind) { out.set('kind', state.filters.kind); }
    if (state.filters.region) { out.set('region', state.filters.region); }
    SIM_KEYS.forEach(function (key) { if (state.sim[key] !== d.sim[key]) { out.set(key, String(state.sim[key])); } });
    return out.toString();
  }

  return {parse: parse, serialize: serialize};
})();

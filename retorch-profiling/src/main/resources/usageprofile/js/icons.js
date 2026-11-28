/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Icons of the cloud providers and of the kinds of alternative (VM, CONTAINER), shown next to the name of the alternative (filters,
   table, charts, titles). The svg texts are embedded by the generator in <script type="application/json" id="icons"> as
   {"providers": {"<provider label>": "<svg>"}, "kinds": {"<kind>": "<svg>"}}, only for what the report has; this script reads that
   block once. A provider or kind without icon shows none.
   Theme: in the dark theme the dark neutral fills (e.g. the navy of the AWS logo) are replaced by the foreground colour of the
   theme so they stay visible; the other colours are kept. Icons are decorative (the name is always written next to them). */
const Icons = (function () {
  const SIZE = 16; // px, for the images of the charts and the width/height of the <img>
  const DARK_LUMINANCE = 0.2; // below this (and nearly grey) a fill is "dark ink"
  const NEUTRAL_CHROMA = 0.2; // (max - min) / 255 below this is "nearly grey"
  let source = null; // {providers: {name: svg}, kinds: {name: svg}}, read once
  let cache = {key: '', uris: {}};

  function sources() {
    if (source) { return source; }
    source = {providers: {}, kinds: {}};
    try {
      const element = document.getElementById('icons');
      const parsed = element ? JSON.parse(element.textContent) : {};
      Object.keys(source).forEach(function (group) {
        Object.keys(parsed[group] || {}).forEach(function (name) { source[group][name] = String(parsed[group][name]); });
      });
    } catch (e) { /* no icons: the page works without them */ }
    return source;
  }

  function hasIn(group, name) { return Object.prototype.hasOwnProperty.call(sources()[group], name); }
  function has(provider) { return hasIn('providers', provider); }
  function hasKind(kind) { return hasIn('kinds', kind); }

  function channels(hex) {
    const h = hex.length === 4 ? '#' + hex.slice(1).split('').map(function (c) { return c + c; }).join('') : hex;
    return [1, 3, 5].map(function (i) { return parseInt(h.slice(i, i + 2), 16) / 255; });
  }

  function luminance(rgb) {
    const lin = rgb.map(function (v) { return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
  }

  /* Dark and nearly grey: ink that vanishes on a dark background. */
  function isDarkInk(hex) {
    const rgb = channels(hex);
    return luminance(rgb) < DARK_LUMINANCE && Math.max.apply(null, rgb) - Math.min.apply(null, rgb) < NEUTRAL_CHROMA;
  }

  /* The svg of the icon for the theme in effect. */
  function themed(group, name) {
    const svg = sources()[group][name];
    if (Theme.effective() !== 'dark') { return svg; }
    const fg = Theme.colors().fg;
    return svg.replace(/fill="(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})"/g, function (all, hex) { return isDarkInk(hex) ? 'fill="' + fg + '"' : all; });
  }

  /* Data URI of the icon for the theme in effect (cached until the theme or its foreground colour changes), or '' without icon. */
  function uriOf(group, name) {
    if (!hasIn(group, name)) { return ''; }
    const key = Theme.effective() + Theme.colors().fg;
    if (cache.key !== key) { cache = {key: key, uris: {}}; }
    const id = group + '/' + name;
    if (!cache.uris[id]) { cache.uris[id] = 'data:image/svg+xml,' + encodeURIComponent(themed(group, name)); }
    return cache.uris[id];
  }

  function uri(provider) { return uriOf('providers', provider); }
  function kindUri(kind) { return uriOf('kinds', kind); }

  /* Decorative <img> (trusted fragment) or nothing: the name always goes next to it, so alt is empty. */
  function imgOf(group, name) {
    return hasIn(group, name) ? Html.t`<img class="prov-icon" src="${uriOf(group, name)}" alt="" width="16" height="16">` : Html.raw('');
  }

  function img(provider) { return imgOf('providers', provider); }
  function kindImg(kind) { return imgOf('kinds', kind); }

  /* The icons of an alternative: provider and kind. */
  function both(provider, kind) { return Html.t`${imgOf('providers', provider)}${imgOf('kinds', kind)}`; }

  /* ECharts axis labels with the icons before the text. providers and kinds: the provider and the kind of every category, in the
     order of the categories (kinds is optional). Returns {rich, formatter}: use rich as axisLabel.rich and formatter as
     axisLabel.formatter (value, index). The image styles are i_<n> (providers) and k_<n> (kinds). */
  function rich(providers, kinds) {
    const styles = {};
    const lists = {i: {group: 'providers', names: [], of: providers}, k: {group: 'kinds', names: [], of: kinds || []}};
    Object.keys(lists).forEach(function (prefix) {
      const l = lists[prefix];
      l.of.forEach(function (name) {
        if (hasIn(l.group, name) && l.names.indexOf(name) < 0) {
          l.names.push(name);
          styles[prefix + '_' + (l.names.length - 1)] = {width: SIZE, height: SIZE, backgroundColor: {image: uriOf(l.group, name)}};
        }
      });
    });
    const formatter = function (value, index) {
      let text = '';
      Object.keys(lists).forEach(function (prefix) {
        const at = lists[prefix].names.indexOf(lists[prefix].of[index]);
        if (at >= 0) { text += '{' + prefix + '_' + at + '|} '; }
      });
      return text + value;
    };
    return {rich: styles, formatter: formatter};
  }

  return {has: has, hasKind: hasKind, uri: uri, kindUri: kindUri, img: img, kindImg: kindImg, both: both, rich: rich, isDarkInk: isDarkInk, SIZE: SIZE};
})();

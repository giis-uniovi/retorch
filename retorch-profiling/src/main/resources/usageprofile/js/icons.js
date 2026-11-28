/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Icons of the cloud providers, shown next to the name of the provider (filters, table, charts, titles). The svg texts are
   embedded by the generator in <script type="application/json" id="icons"> as {"<provider label>": "<svg>"}, only for the
   providers of the report; this script reads that block once. A provider without icon shows none.
   Theme: in the dark theme the dark neutral fills (e.g. the navy of the AWS logo) are replaced by the foreground colour of the
   theme so they stay visible; the other colours are kept. Icons are decorative (the name is always written next to them). */
const Icons = (function () {
  const SIZE = 16; // px, for the images of the charts and the width/height of the <img>
  const DARK_LUMINANCE = 0.2; // below this (and nearly grey) a fill is "dark ink"
  const NEUTRAL_CHROMA = 0.2; // (max - min) / 255 below this is "nearly grey"
  let source = null; // {provider: svg}, read once
  let cache = {key: '', uris: {}};

  function sources() {
    if (source) { return source; }
    source = {};
    try {
      const element = document.getElementById('icons');
      const parsed = element ? JSON.parse(element.textContent) : {};
      Object.keys(parsed).forEach(function (name) { source[name] = String(parsed[name]); });
    } catch (e) { /* no icons: the page works without them */ }
    return source;
  }

  function has(provider) { return Object.prototype.hasOwnProperty.call(sources(), provider); }

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

  /* The svg of the provider for the theme in effect. */
  function themed(provider) {
    const svg = sources()[provider];
    if (Theme.effective() !== 'dark') { return svg; }
    const fg = Theme.colors().fg;
    return svg.replace(/fill="(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3})"/g, function (all, hex) { return isDarkInk(hex) ? 'fill="' + fg + '"' : all; });
  }

  /* Data URI of the icon for the theme in effect (cached until the theme or its foreground colour changes), or '' without icon. */
  function uri(provider) {
    if (!has(provider)) { return ''; }
    const key = Theme.effective() + Theme.colors().fg;
    if (cache.key !== key) { cache = {key: key, uris: {}}; }
    if (!cache.uris[provider]) { cache.uris[provider] = 'data:image/svg+xml,' + encodeURIComponent(themed(provider)); }
    return cache.uris[provider];
  }

  /* Decorative <img> (trusted fragment) or nothing: the provider name always goes next to it, so alt is empty. */
  function img(provider) {
    return has(provider) ? Html.t`<img class="prov-icon" src="${uri(provider)}" alt="" width="16" height="16">` : Html.raw('');
  }

  /* ECharts axis labels with the icon before the text. providers: the provider of every category, in the order of the
     categories. Returns {rich, formatter}: use rich as axisLabel.rich and formatter as axisLabel.formatter (value, index). */
  function rich(providers) {
    const rich = {};
    const names = [];
    providers.forEach(function (p) {
      if (has(p) && names.indexOf(p) < 0) {
        names.push(p);
        rich['i_' + (names.length - 1)] = {width: SIZE, height: SIZE, backgroundColor: {image: uri(p)}};
      }
    });
    const formatter = function (value, index) {
      const at = names.indexOf(providers[index]);
      return at < 0 ? String(value) : '{i_' + at + '|} ' + value;
    };
    return {rich: rich, formatter: formatter};
  }

  return {has: has, uri: uri, img: img, rich: rich, isDarkInk: isDarkInk, SIZE: SIZE};
})();

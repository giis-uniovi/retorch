/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {JS_DIR, load} from './helpers/load.mjs';

const CSS = fs.readFileSync(path.resolve(JS_DIR, '..', 'usageprofile.css'), 'utf8').split('\r\n').join('\n');

/* The custom properties declared inside the block that starts with this selector (the theme tokens). */
function tokens(selector) {
  const start = CSS.indexOf('  ' + selector + ' {');
  assert.ok(start >= 0, selector);
  const body = CSS.slice(start, CSS.indexOf('\n  }', start));
  const found = {};
  for (const m of body.matchAll(/^\s*(--[a-z0-9-]+):\s*([^;]+);/gm)) { found[m[1]] = m[2].trim(); }
  return found;
}

const light = tokens(':root');
const dark = Object.assign({}, light, tokens('html[data-theme="dark"]'));

const luminance = (hex) => {
  const h = hex.length === 4 ? '#' + [...hex.slice(1)].map((c) => c + c).join('') : hex;
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a, b) => { const [x, y] = [luminance(a), luminance(b)]; return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };

test('Theme.colors() reads the text colours and the chart palettes from the CSS custom properties', () => {
  const {Theme} = load(['theme.js'], ['Theme'], {vars: light});
  const c = Theme.colors();
  assert.equal(c.fg, light['--fg']);
  assert.equal(c.card, light['--card']);
  assert.equal(c.phase['tjob-testexec'], light['--phase-tjob-testexec']);
  assert.equal(c.object.browser, light['--object-browser']);
  assert.equal(c.series.over, light['--series-over']);
  assert.equal(c.cycle.length, 10);
  assert.equal(c.cycle[3], light['--cycle-3']);
});

test('the colours are cached until the theme changes', () => {
  const vars = Object.assign({}, light);
  const {Theme} = load(['theme.js'], ['Theme'], {vars});
  assert.equal(Theme.colors().fg, light['--fg']);
  vars['--fg'] = '#000000';
  assert.equal(Theme.colors().fg, light['--fg'], 'cached');
  Theme.set('dark', false, true);
  assert.equal(Theme.colors().fg, '#000000', 'read again after a change of theme');
});

test('every palette colour keeps 3:1 against the card in both themes (WCAG 1.4.11)', () => {
  const names = Object.keys(light).filter((k) => /^--(phase|object|series|cycle)-/.test(k));
  assert.ok(names.length >= 24);
  for (const [theme, values] of [['light', light], ['dark', dark]]) {
    for (const name of names) {
      assert.ok(contrast(values[name], values['--card']) >= 3, `${name} in ${theme}: ${contrast(values[name], values['--card']).toFixed(2)}`);
    }
  }
});

test('the palettes Theme reads and the ones the CSS declares are the same', () => {
  const declared = Object.keys(light).filter((k) => /^--(phase|object|series|cycle)-/.test(k)).sort();
  const {Theme} = load(['theme.js'], ['Theme'], {vars: Object.fromEntries(Object.entries(light).map(([k, v]) => [k, v]))});
  const c = Theme.colors();
  const read = [];
  for (const [palette, map] of [['phase', c.phase], ['object', c.object], ['series', c.series]]) { for (const k of Object.keys(map)) { read.push(`--${palette}-${k}`); } }
  c.cycle.forEach((v, i) => read.push(`--cycle-${i}`));
  assert.deepEqual(read.sort(), declared);
  for (const k of read) { assert.ok(light[k], k + ' is empty'); }
});

test('the CSS keeps the tokens one per line and has no !important', () => {
  const tokenLines = CSS.split('\n').filter((l) => /^\s+--[a-z0-9-]+:/.test(l));
  assert.ok(tokenLines.length > 40);
  for (const line of tokenLines) { assert.equal((line.match(/--[a-z0-9-]+:/g) || []).length, 1, line); }
  assert.ok(!CSS.includes('!important'), 'the layers make !important unnecessary');
  for (const section of ['tokens', 'base', 'layout', 'components', 'charts', 'responsive', 'utilities', 'forced']) {
    assert.ok(CSS.includes('@layer ' + section + ' {'), section);
  }
});

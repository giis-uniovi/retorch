/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';

const {Fmt, I18n} = load(['i18n.js', 'format.js'], ['Fmt', 'I18n']);

test('esc escapes the five HTML special characters', () => {
  assert.equal(Fmt.esc('<a href="x">&</a>'), '&lt;a href=&quot;x&quot;&gt;&amp;&lt;/a&gt;');
});

test('usd uses more digits for small amounts', () => {
  I18n.set('en', false);
  assert.equal(Fmt.usd(12.3456), '$12.35');
  assert.equal(Fmt.usd(0.5), '$0.500');
  assert.equal(Fmt.usd(0.001), '$0.00100');
});

test('dur picks seconds, minutes or hours', () => {
  I18n.set('en', false);
  assert.equal(Fmt.dur(30), '30 s');
  assert.equal(Fmt.dur(90), '1.5 min');
  assert.equal(Fmt.dur(7200), '2.0 h');
});

test('orDash shows a dash for a missing measurement', () => {
  assert.equal(Fmt.orDash(null, Fmt.usd), '–');
  assert.equal(Fmt.orDash(undefined, Fmt.usd), '–');
  assert.equal(Fmt.orDash(2, Fmt.usd), '$2.00');
});

test('every phase has a colour and a pattern', () => {
  Fmt.PHASES.forEach((p) => { assert.ok(Fmt.PHASE_COLORS[p], p); assert.ok(Fmt.PHASE_PATTERNS[p], p); });
});

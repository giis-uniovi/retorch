/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';
import {texts} from './helpers/texts.mjs';

const {Fmt, I18n} = load(['i18n.js', 'format.js'], ['Fmt', 'I18n']);
I18n.load(texts);

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

test('every phase has a fill pattern, a second cue besides the colour', () => {
  ['coi-setup', 'tjob-setup', 'tjob-testexec', 'tjob-teardown', 'coi-teardown', 'oversubscription'].forEach((p) => assert.ok(Fmt.PHASE_PATTERNS[p], p));
});

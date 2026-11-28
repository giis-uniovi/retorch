/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {JS_DIR, load} from './helpers/load.mjs';

import {LANGS, RESOURCES, texts} from './helpers/texts.mjs';

const placeholders = (s) => (String(s).match(/\{[A-Za-z]+\}/g) || []).sort().join(',');

test('every language has exactly the keys of English, with a text and the same placeholders', () => {
  const english = Object.keys(texts.en);
  assert.ok(english.length > 100);
  for (const lang of LANGS) {
    const keys = Object.keys(texts[lang]);
    assert.deepEqual(english.filter((k) => !keys.includes(k)), [], lang + ' lacks keys');
    assert.deepEqual(keys.filter((k) => !english.includes(k)), [], lang + ' has keys that English does not have');
    for (const key of english) {
      assert.ok(String(texts[lang][key]).trim() !== '', lang + ' has an empty text for ' + key);
      assert.equal(placeholders(texts[lang][key]), placeholders(texts.en[key]), lang + ' changes the placeholders of ' + key);
    }
  }
});

test('the language files are the ones I18n lists', () => {
  const {I18n} = load(['i18n.js'], ['I18n']);
  assert.deepEqual([...I18n.LANGS].sort(), fs.readdirSync(path.join(RESOURCES, 'i18n')).map((f) => f.replace('.json', '')).sort());
});

test('every key the scripts and the page use literally exists in English', () => {
  const sources = [path.join(RESOURCES, 'usageprofile.html')];
  const walk = (dir) => fs.readdirSync(dir, {withFileTypes: true}).forEach((e) => (e.isDirectory() ? walk(path.join(dir, e.name)) : sources.push(path.join(dir, e.name))));
  walk(JS_DIR);
  const used = new Set();
  for (const file of sources) {
    const code = fs.readFileSync(file, 'utf8');
    // a literal key ends with a name; 'sim.pay.' + x is a prefix of a computed key and is skipped
    for (const m of code.matchAll(/\b(?:I18n\.)?t\(\s*'([a-z][A-Za-z0-9.-]*\.[A-Za-z0-9.-]*[A-Za-z0-9-])'/g)) { used.add(m[1]); }
    for (const m of code.matchAll(/data-i18n(?:-title|-alt|-label|-tip)?="([^"]+)"/g)) { used.add(m[1]); }
  }
  assert.ok(used.size > 50);
  assert.deepEqual([...used].filter((k) => !(k in texts.en)), []);
});

test('t() uses the current language, then English, then the key, and replaces {params}', () => {
  const {I18n} = load(['i18n.js'], ['I18n']);
  I18n.load({en: {a: 'A {x}', b: 'B'}, es: {a: 'AA {x}'}});
  I18n.set('es', false);
  assert.equal(I18n.t('a', {x: 1}), 'AA 1');
  assert.equal(I18n.t('b'), 'B');
  assert.equal(I18n.t('missing.key'), 'missing.key');
  I18n.set('xx', false);
  assert.equal(I18n.lang(), 'en');
});

test('with the real texts every language translates the title and the phases', () => {
  const {I18n} = load(['i18n.js'], ['I18n']);
  I18n.load(texts);
  for (const lang of LANGS) {
    I18n.set(lang, false);
    assert.equal(I18n.t('app.title'), 'RETORCH Resource Usage Profile');
    assert.notEqual(I18n.t('phase.tjob-testexec'), 'phase.tjob-testexec');
    assert.notEqual(I18n.t('kind.VM'), 'kind.VM');
  }
});

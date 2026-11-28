/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Loads the dashboard scripts (IIFE namespaces declared with const, as in the page: one global scope) into a vm context with
   minimal browser stubs, so the pure logic can be tested with node --test. */
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';

export const JS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../main/resources/usageprofile/js');

/* files: script names relative to js/ (e.g. 'format.js'); names: the namespaces to return (e.g. ['Fmt']). */
export function load(files, names, options) {
  const opts = options || {};
  const store = {};
  const documentStub = {
    documentElement: {setAttribute() {}, getAttribute() { return null; }, lang: 'en', style: {}},
    getElementById() { return null; },
    querySelectorAll() { return []; },
    title: ''
  };
  const sandbox = {
    window: {matchMedia: null, innerWidth: 1300, devicePixelRatio: 1, localStorage: {getItem: (k) => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }},
      addEventListener() {}, location: {hash: ''}, history: {replaceState() {}}},
    document: documentStub,
    navigator: {language: opts.language || 'en'},
    getComputedStyle: () => ({fontSize: '16px', getPropertyValue: () => ''}),
    console, URLSearchParams, encodeURIComponent, decodeURIComponent
  };
  sandbox.window.document = documentStub;
  const context = vm.createContext(sandbox);
  const source = files.map((f) => fs.readFileSync(path.join(JS_DIR, f), 'utf8')).join('\n');
  return vm.runInContext(source + '\n;({' + names.join(',') + '})', context, {filename: files.join('+')});
}

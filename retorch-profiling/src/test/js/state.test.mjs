/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';
import {ctx} from './helpers/ctx.mjs';

const {State} = load(['model/simulation.js', 'model/state.js'], ['State']);
const plain = (v) => JSON.parse(JSON.stringify(v));

test('the initial state is the default one: first alternative selected, the cheapest compared, nothing filtered', () => {
  const s = State.create(ctx).get();
  assert.equal(s.selectedAlt, 'Azure/westeurope/D2s');
  assert.deepEqual(plain(s.compared), ['Azure/westeurope/D2s', 'Fargate']);
  assert.deepEqual(plain(s.filters), {offProviders: [], kind: '', region: ''});
  assert.equal(s.node, null);
  assert.deepEqual(plain(s.sim), {runs: 4, days: 22, pattern: 'perRun', hours: 10, disc: 0, buy: 'od'});
});

test('update returns the changed keys and ignores what does not change', () => {
  const store = State.create(ctx);
  assert.deepEqual(plain(store.update({sort: 'provider', metric: 'usd'})), ['sort']);
  assert.deepEqual(plain(store.update({sort: 'provider'})), []);
  assert.equal(store.get().sort, 'provider');
});

test('invalid values are ignored: unknown ids, enums, out of range numbers', () => {
  const store = State.create(ctx);
  const before = JSON.stringify(store.get());
  const changed = store.update({selectedAlt: 'nope', node: 'nope', sort: 'size', metric: 'eur', cmpGroup: 'x', group: 'y', contMetric: 'z', lang: 'it',
    theme: 'blue', showOver: 'yes', filters: {kind: 'GPU', region: 'mars', offProviders: 'AWS'}, sim: {runs: 0, days: 40, hours: 99, disc: -1, pattern: 'x', buy: 'nope'}, unknown: 1});
  assert.deepEqual(plain(changed), []);
  assert.equal(JSON.stringify(store.get()), before);
});

test('filters and sim merge key by key', () => {
  const store = State.create(ctx);
  store.update({filters: {kind: 'VM'}});
  store.update({filters: {offProviders: ['AWS']}});
  assert.deepEqual(plain(store.get().filters), {offProviders: ['AWS'], kind: 'VM', region: ''});
  store.update({sim: {runs: 9}});
  store.update({sim: {pattern: 'hours', buy: '1-NO_UPFRONT'}});
  assert.deepEqual(plain(store.get().sim), {runs: 9, days: 22, pattern: 'hours', hours: 10, disc: 0, buy: '1-NO_UPFRONT'});
});

test('compared keeps the order of the alternatives and drops duplicates and unknown ids', () => {
  const store = State.create(ctx);
  store.update({compared: ['Fargate', 'nope', 'Azure/westeurope/D4s', 'Fargate']});
  assert.deepEqual(plain(store.get().compared), ['Azure/westeurope/D4s', 'Fargate']);
  store.update({compared: []});
  assert.deepEqual(plain(store.get().compared), []);
});

test('subscribers are called with the changed keys until they unsubscribe', () => {
  const store = State.create(ctx);
  const calls = [];
  const off = store.subscribe((keys, state) => calls.push([plain(keys), state.node]));
  store.update({node: 'tjoba'});
  store.update({node: 'tjoba'});
  off();
  store.update({node: null});
  assert.deepEqual(calls, [[['node'], 'tjoba']]);
});

test('resolve gives a complete state: the link is the default for what it does not say, except what is kept', () => {
  const full = State.resolve({sort: 'over', sim: {runs: 7}}, ctx, {lang: 'fr', theme: 'dark'});
  assert.equal(full.lang, 'fr');
  assert.equal(full.theme, 'dark');
  assert.equal(full.sort, 'over');
  assert.equal(full.metric, 'usd');
  assert.deepEqual(plain(full.sim), {runs: 7, days: 22, pattern: 'perRun', hours: 10, disc: 0, buy: 'od'});
});

/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';
import {ctx} from './helpers/ctx.mjs';

const {State, Hash} = load(['model/simulation.js', 'model/state.js', 'model/hash.js'], ['State', 'Hash']);
const plain = (v) => JSON.parse(JSON.stringify(v));

/* Applies the patch, writes the hash, reads it into a new state and checks that it is the same state. Returns the hash. */
const roundTrip = (patch) => {
  const store = State.create(ctx);
  store.update(patch);
  const before = plain(store.get());
  const text = Hash.serialize(store.get(), ctx);
  const again = State.create(ctx);
  again.update(State.resolve(Hash.parse('#' + text, ctx), ctx, {lang: ctx.lang, theme: 'auto'}));
  assert.deepEqual(plain(again.get()), before, text);
  return text;
};

test('the default state has an empty hash', () => {
  assert.equal(Hash.serialize(State.create(ctx).get(), ctx), '');
});

test('parse(serialize(state)) is the state, for every key', () => {
  roundTrip({lang: 'fr', theme: 'dark', selectedAlt: 'AWS/eu-west-1/m5.large', node: 'tjobb', showOver: false, metric: 'pct', sort: 'provider',
    cmpGroup: 'tjob', group: 'object', contMetric: 'cpu', compared: ['Azure/westeurope/D4s', 'x,y%z'],
    filters: {offProviders: ['AWS'], kind: 'VM', region: 'westeurope'},
    sim: {runs: 7.5, days: 20, pattern: 'hours', hours: 8, disc: 10, buy: '3-ALL_UPFRONT'}});
});

test('only what differs from the default is written', () => {
  assert.equal(roundTrip({sort: 'over'}), 'sort=over');
  assert.equal(roundTrip({lang: 'de'}), 'lang=de');
  assert.equal(roundTrip({sim: {runs: 9}}), 'runs=9');
});

test('the comparison is written as all, none or a list of ids', () => {
  assert.equal(roundTrip({compared: ctx.altIds}), 'cmp=all');
  assert.equal(roundTrip({compared: []}), 'cmp=none');
  assert.match(roundTrip({compared: ['Fargate', 'x,y%z']}), /^cmpids=/);
});

test('the older link keys are read: cmp=cheapest, over=0, simulator keys', () => {
  const patch = Hash.parse('#lang=es&theme=light&node=tjoba&alt=Fargate&over=0&metric=pct&sort=provider&cmpgroup=tjob&cmp=cheapest&runs=7&days=20&pattern=hours&hours=8&disc=10&buy=1-NO_UPFRONT', ctx);
  assert.equal(patch.lang, 'es');
  assert.equal(patch.showOver, false);
  assert.deepEqual(plain(patch.compared), plain(State.defaults(ctx).compared));
  assert.deepEqual(plain(patch.sim), {runs: 7, days: 20, pattern: 'hours', hours: 8, disc: 10, buy: '1-NO_UPFRONT'});
});

test('invalid values and unknown keys are ignored', () => {
  const patch = Hash.parse('#lang=it&theme=blue&alt=nope&node=nope&metric=eur&sort=size&cmpgroup=x&cmp=few&runs=0&days=99&hours=abc&disc=-5&pattern=never&buy=nope&kind=GPU&region=mars&noprov=Oracle&zzz=1', ctx);
  assert.deepEqual(plain(patch), {filters: {offProviders: []}, sim: {}});
});

test('a link with bad escapes does not break the page', () => {
  assert.doesNotThrow(() => Hash.parse('#cmpids=%E0%A4%A&noprov=%', ctx));
  assert.doesNotThrow(() => Hash.parse('', ctx));
  assert.deepEqual(plain(Hash.parse('', ctx)), {});
});

/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';

const {Profile} = load(['model/profile.js'], ['Profile']);
const plain = (v) => JSON.parse(JSON.stringify(v)); // values built inside the vm context have other prototypes

const item = (object, tjob, phase, cost) => ({object, tjob, phase, cost});
const alt = (id, provider, kind, region, items, extra) => Object.assign({
  id, provider, kind, label: id, region, hourlyPrice: 1, items, total: items.reduce((s, i) => s + i.cost, 0), reservations: []}, extra);

const A = alt('a', 'Azure', 'VM', 'r1', [item('compute', 't1', 'tjob-testexec', 3), item('compute', 't1', 'oversubscription', 1), item('storage', 't2', 'tjob-setup', 0.5)]);
const B = alt('b', 'AWS', 'CONTAINER', 'r1', [item('compute', 't1', 'tjob-testexec', 2), item('compute', 't2', 'tjob-setup', 1)]);
const C = alt('c', 'AWS', 'BROWSER_SERVICE', 'r2', [item('browser', 't1', 'tjob-testexec', 9)]);
const node = (id, stage, start, setup, exec, tear, predecessors) => ({id, stage, startSec: start, setupSec: setup, execSec: exec, teardownSec: tear,
  predecessors: predecessors || [], resources: [], tests: [], containers: []});
const data = {alternatives: [A, B, C], graph: {nodes: [node('t2', 1, 40, 10, 20, 10, ['t1']), node('t1', 0, 0, 10, 20, 10), node('t3', 1, null, null, null, null, ['t1'])]}};

test('sum, idle and the cost split by phase, object and TJob', () => {
  const p = Profile.create(data);
  assert.equal(Profile.sum(A.items), 4.5);
  assert.equal(Profile.idle(A), 1);
  assert.deepEqual(plain(p.costBy(A, 'phase')), [{key: 'tjob-setup', cost: 0.5}, {key: 'tjob-testexec', cost: 3}, {key: 'oversubscription', cost: 1}]);
  assert.deepEqual(plain(p.costBy(A, 'object')), [{key: 'compute', cost: 4}, {key: 'storage', cost: 0.5}]);
  assert.deepEqual(plain(p.costBy(A, 'tjob')), [{key: 't1', cost: 4}, {key: 't2', cost: 0.5}]);
});

test('the cost matrix has one array per column with one value per row', () => {
  const m = Profile.create(data).matrix(A, 'tjob', 'phase', ['t1', 't2'], ['tjob-setup', 'tjob-testexec']);
  assert.deepEqual(plain(m.values), [[0, 0.5], [3, 0]]);
});

test('cost table of several alternatives by phase and by TJob', () => {
  const p = Profile.create(data);
  const byPhase = p.costTable([A, B], 'phase');
  assert.deepEqual(plain(byPhase.values['tjob-testexec']), [3, 2]);
  assert.deepEqual(plain(byPhase.values['coi-setup']), [0, 0]);
  assert.deepEqual(plain(p.costTable([A, B], 'tjob').keys), ['t1', 't2']);
});

test('filters by provider, kind and region; kinds follow the meta order', () => {
  const p = Profile.create(data);
  assert.deepEqual(plain(p.alternatives({offProviders: new Set(['AWS'])}).map((a) => a.id)), ['a']);
  assert.deepEqual(plain(p.alternatives({kind: 'BROWSER_SERVICE'}).map((a) => a.id)), ['c']);
  assert.deepEqual(plain(p.alternatives({region: 'r1'}).map((a) => a.id)), ['a', 'b']);
  assert.deepEqual(plain(p.kinds()), ['VM', 'CONTAINER', 'BROWSER_SERVICE']);
  assert.equal(p.alternatives().length, 3);
});

test('the label adds the region only when a provider has VMs in several regions', () => {
  assert.equal(Profile.create(data).label(A), 'Azure · a');
  const two = Profile.create({alternatives: [A, alt('a2', 'Azure', 'VM', 'r2', A.items)]});
  assert.equal(two.label(A), 'Azure · a [r1]');
});

test('cheapest and sorted', () => {
  const p = Profile.create(data);
  assert.deepEqual(plain(p.cheapest(2).map((a) => a.id)), ['b', 'a']);
  assert.deepEqual(plain(p.sorted([A, B, C], 'cost').map((a) => a.id)), ['b', 'a', 'c']);
  assert.deepEqual(plain(p.sorted([A, B, C], 'provider').map((a) => a.id)), ['b', 'c', 'a']);
  assert.deepEqual(plain(p.sorted([A, B, C], 'over').map((a) => a.id)), ['a', 'b', 'c']);
});

test('chart rows in USD or as a percentage of the cheapest shown, with or without oversubscription', () => {
  const p = Profile.create(data);
  const usd = p.chartRows([A, B], {showOver: true, metric: 'usd'});
  assert.deepEqual(plain(usd), [{label: 'Azure · a', used: 3.5, over: 1}, {label: 'AWS · b', used: 3, over: 0}]);
  const pct = p.chartRows([A, B], {showOver: false, metric: 'pct'});
  assert.equal(pct[0].used, 100 * 3.5 / 3);
  assert.equal(pct[0].over, 0);
  assert.equal(pct[1].used, 100);
});

test('Gantt layout: stage order, segments and a placeholder that starts after its predecessors', () => {
  const lay = Profile.create(data).ganttLayout();
  assert.deepEqual(plain(lay.rows.map((r) => r.node.id)), ['t1', 't2', 't3']);
  assert.equal(lay.longest, 80);
  assert.equal(lay.byId.t2.end, 80);
  assert.equal(lay.byId.t3.noData, true);
  assert.equal(lay.byId.t3.start, 40); // ends of t1
  assert.equal(lay.byId.t3.end, 40 + 80 * 0.03);
});

test('span of the graph is its longest end, 0 without graph', () => {
  assert.equal(Profile.create(data).span(), 80);
  assert.equal(Profile.create({alternatives: [], graph: null}).span(), 0);
});

test('TJob duration is the sum of the measured phases, null without any', () => {
  assert.equal(Profile.duration(node('x', 0, 0, 1, 2, 3)), 6);
  assert.equal(Profile.duration(node('x', 0, null, null, null, null)), null);
  assert.equal(Profile.measured(node('x', 0, null, null, null, null)), false);
});

test('storage of a TJob counts a shared image once', () => {
  const c = (name, image, layer, img, vol) => ({name, image, layerGb: layer, imageGb: img, volumeGb: vol});
  assert.equal(Profile.tjobStorage([c('a', 'app:1', 1, 2, 0), c('b', 'app:1', 1, 2, 0.5), c('c', 'redis', 0, 1, 0)]), 1 + 1 + 0.5 + 2 + 1);
});

test('the lists of phases, kinds and objects come from meta, with a fallback', () => {
  const none = Profile.create(data).meta;
  assert.deepEqual(plain(none.phases), ['coi-setup', 'tjob-setup', 'tjob-testexec', 'tjob-teardown', 'coi-teardown', 'oversubscription']);
  assert.deepEqual(plain(none.objects), ['compute', 'storage', 'browser']);
  const given = Profile.create({alternatives: [], meta: {phases: ['p1', {label: 'p2'}], kinds: ['K'], objects: ['o']}}).meta;
  assert.deepEqual(plain(given), {phases: ['p1', 'p2'], kinds: ['K'], objects: ['o']});
  const partial = Profile.create({alternatives: [], meta: {phases: []}}).meta;
  assert.equal(partial.phases.length, 6);
});

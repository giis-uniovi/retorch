/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';

const {Simulation} = load(['model/simulation.js'], ['Simulation']);
const calc = Simulation.calc;
const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, a + ' != ' + b);

const vmAlt = {id: 'vm', kind: 'VM', hourlyPrice: 1, total: 0.5, items: [
  {object: 'compute', tjob: 'a', phase: 'tjob-testexec', cost: 0.4}, {object: 'storage', tjob: 'a', phase: 'tjob-testexec', cost: 0.1}],
  reservations: [{termYears: 1, payment: 'ALL_UPFRONT', effectiveHourly: 0.5}]};
const containerAlt = {id: 'c', kind: 'CONTAINER', hourlyPrice: 0, total: 0.2, items: [{object: 'compute', tjob: 'a', phase: 'tjob-testexec', cost: 0.2}], reservations: []};

test('storage and compute of a run are split by cloud object', () => {
  near(calc.storageRun(vmAlt), 0.1);
  near(calc.computeRun(vmAlt), 0.4);
});

test('per execution: runs x days x (compute x (1 - discount) + storage)', () => {
  near(calc.perRunMonthly(0.4, 0.1, 4, 22, 0), 4 * 22 * 0.5);
  near(calc.perRunMonthly(0.4, 0.1, 4, 22, 50), 4 * 22 * 0.3);
});

test('always on bills 730 hours and the disk all month', () => {
  near(calc.onDemandMonthly(1, 0, 730, 0.1, 3600), 730 + 0.1 * 730);
  near(calc.reservedMonthly(0.5, 0.1, 3600), 0.5 * 730 + 0.1 * 730);
});

test('utilisation above 100 percent does not fit; max runs per day is floor(86400 / T)', () => {
  assert.equal(calc.utilisation(10, 3600, 24) > 1, false);
  assert.equal(calc.utilisation(30, 3600, 24) > 1, true);
  assert.equal(calc.maxRuns(3600, 24), 24);
  assert.equal(calc.maxRuns(0, 24), Infinity);
});

test('break-even runs per day = always-on monthly / (days x per-run cost)', () => {
  near(calc.breakEven(110, 22, 0.5), 10);
  assert.equal(calc.breakEven(110, 22, 0), Infinity);
});

test('a container service is always pay per use; the cheapest fitting alternative is the best', () => {
  const p = {runs: 4, days: 22, pattern: 'always', hours: 10, disc: 0, buy: 'od'};
  const result = Simulation.compute([vmAlt, containerAlt], 3600, p);
  const byId = {};
  result.rows.forEach((r) => { byId[r.alt.id] = r; });
  assert.equal(byId.c.mode, 'perUse');
  assert.equal(byId.vm.mode, 'od24');
  assert.equal(result.best, result.rows[0]);
  assert.ok(result.rows[0].monthly <= result.rows[1].monthly);
});

test('a reservation is applied only to the always-on patterns', () => {
  const res = vmAlt.reservations[0];
  const perRun = Simulation.project(vmAlt, 3600, {runs: 4, days: 22, pattern: 'perRun', hours: 10, disc: 0, buy: '1-ALL_UPFRONT'}, res);
  assert.equal(perRun.mode, 'perRun');
  const always = Simulation.project(vmAlt, 3600, {runs: 4, days: 22, pattern: 'always', hours: 10, disc: 0, buy: '1-ALL_UPFRONT'}, res);
  assert.equal(always.mode, 'res');
  near(always.monthly, 0.5 * 730 + 0.1 * 730);
});

test('reservation keys are sorted by term and payment', () => {
  const keys = Simulation.reservationKeys([{reservations: [{termYears: 3, payment: 'ALL_UPFRONT'}, {termYears: 1, payment: 'ALL_UPFRONT'}, {termYears: 1, payment: 'NO_UPFRONT'}]}]);
  assert.deepEqual([...keys], ['1-NO_UPFRONT', '1-ALL_UPFRONT', '3-ALL_UPFRONT']);
});

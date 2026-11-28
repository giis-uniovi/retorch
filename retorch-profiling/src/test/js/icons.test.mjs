/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';

const plain = (x) => JSON.parse(JSON.stringify(x)); // objects of the page context compare by value

const NAVY = '#252f3e';
const ORANGE = '#ff9900';
const BLUE = '#0c59a2'; // dark but saturated: it keeps its colour
const SVGS = {
  AWS: `<svg viewBox="0 0 10 10"><path fill="${NAVY}" d="M0 0"/><path fill="${ORANGE}" d="M1 1"/></svg>`,
  Azure: `<svg viewBox="0 0 10 10"><path fill="${BLUE}" d="M0 0"/><path fill="#000" d="M1 1"/></svg>`,
  'Odd & <Name>': '<svg viewBox="0 0 1 1"><path fill="#ffffff" d="M0 0"/></svg>'
};
const KINDS = {
  VM: '<svg viewBox="0 0 10 10"><path fill="#5288c8" d="M0 0"/><path fill="#ffffff" d="M1 1"/></svg>',
  DARK: '<svg viewBox="0 0 10 10"><path fill="#1a1a1a" d="M0 0"/></svg>'
};
const VARS = {'--fg': '#e8eaf0'};

function page(block) {
  return load(['html.js', 'theme.js', 'icons.js'], ['Html', 'Theme', 'Icons'], {vars: VARS, elements: block === null ? {} : {icons: {textContent: JSON.stringify(block || {providers: SVGS, kinds: KINDS})}}});
}
const decode = (uri) => decodeURIComponent(uri.slice('data:image/svg+xml,'.length));

test('has() tells the providers that have an icon', () => {
  const {Icons} = page();
  assert.equal(Icons.has('AWS'), true);
  assert.equal(Icons.has('Nobody'), false);
  assert.equal(Icons.has('toString'), false);
});

test('without the icons block nothing has an icon and nothing fails', () => {
  const {Icons} = page(null);
  assert.equal(Icons.has('AWS'), false);
  assert.equal(Icons.uri('AWS'), '');
  assert.equal(String(Icons.img('AWS')), '');
  assert.deepEqual(plain(Object.keys(Icons.rich(['AWS']).rich)), []);
});

test('uri() is a data URI of the svg, escaped with encodeURIComponent', () => {
  const {Icons} = page();
  const uri = Icons.uri('Odd & <Name>');
  assert.ok(uri.startsWith('data:image/svg+xml,%3Csvg'));
  assert.ok(!/[<>"\s]/.test(uri), 'nothing that needs HTML escaping or breaks the URL');
  assert.equal(decode(uri), SVGS['Odd & <Name>']);
  assert.equal(Icons.uri('Nobody'), '');
});

test('img() is a decorative 16 px image for a known provider and nothing for an unknown one', () => {
  const {Icons} = page();
  const html = String(Icons.img('AWS'));
  assert.match(html, /^<img class="prov-icon" src="data:image\/svg\+xml,[^"]+" alt="" width="16" height="16">$/);
  assert.equal(String(Icons.img('Nobody')), '');
});

test('img() works as a trusted fragment inside Html.t', () => {
  const {Icons, Html} = page();
  const html = String(Html.t`<b>${Icons.img('AWS')}${'<x>'}</b>`);
  assert.ok(html.startsWith('<b><img class="prov-icon"'));
  assert.ok(html.endsWith('&lt;x&gt;</b>'));
});

test('light theme: the svg is used as it is', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  assert.equal(decode(Icons.uri('AWS')), SVGS.AWS);
});

test('dark theme: the dark neutral fills take the foreground colour and the other colours stay', () => {
  const {Icons, Theme} = page();
  Theme.set('dark', false, true);
  assert.equal(decode(Icons.uri('AWS')), SVGS.AWS.replace(NAVY, VARS['--fg']));
  assert.ok(decode(Icons.uri('AWS')).includes(ORANGE));
  const azure = decode(Icons.uri('Azure'));
  assert.ok(azure.includes(BLUE), 'a saturated dark blue is a colour, not ink');
  assert.ok(azure.includes('fill="#000"') === false && azure.includes(VARS['--fg']), 'black is ink');
  assert.equal(decode(Icons.uri('Odd & <Name>')), SVGS['Odd & <Name>'], 'white stays white');
});

test('the icons are cached per theme and re-created when the theme changes', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  const light = Icons.uri('AWS');
  assert.equal(Icons.uri('AWS'), light);
  Theme.set('dark', false, true);
  const dark = Icons.uri('AWS');
  assert.notEqual(dark, light);
  Theme.set('light', false, true);
  assert.equal(Icons.uri('AWS'), light);
});

test('rich() gives one image style per provider with an icon and a formatter by index', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  const providers = ['AWS', 'Azure', 'AWS', 'Nobody'];
  const r = Icons.rich(providers);
  assert.deepEqual(plain(Object.keys(r.rich)), ['i_0', 'i_1']);
  assert.deepEqual(plain(r.rich.i_0), {width: 16, height: 16, backgroundColor: {image: Icons.uri('AWS')}});
  assert.equal(r.rich.i_1.backgroundColor.image, Icons.uri('Azure'));
  assert.equal(r.formatter('AWS · m5', 0), '{i_0|} AWS · m5');
  assert.equal(r.formatter('Azure · D2', 1), '{i_1|} Azure · D2');
  assert.equal(r.formatter('AWS · m6', 2), '{i_0|} AWS · m6');
  assert.equal(r.formatter('Nobody · x', 3), 'Nobody · x');
  assert.equal(r.formatter('out of range', 9), 'out of range');
});

test('kinds: hasKind, kindUri and kindImg behave as the provider ones and VM/unknown kinds do not mix with providers', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  assert.equal(Icons.hasKind('VM'), true);
  assert.equal(Icons.hasKind('BROWSER_SERVICE'), false, 'a kind without icon shows none');
  assert.equal(Icons.hasKind('AWS'), false);
  assert.equal(Icons.has('VM'), false);
  assert.equal(decode(Icons.kindUri('VM')), KINDS.VM);
  assert.equal(Icons.kindUri('BROWSER_SERVICE'), '');
  assert.match(String(Icons.kindImg('VM')), /^<img class="prov-icon" src="data:image\/svg\+xml,[^"]+" alt="" width="16" height="16">$/);
  assert.equal(String(Icons.kindImg('BROWSER_SERVICE')), '');
});

test('both() gives the provider icon and then the kind icon, whichever exists', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  const html = String(Icons.both('AWS', 'VM'));
  assert.equal((html.match(/<img /g) || []).length, 2);
  assert.ok(html.indexOf(encodeURIComponent('#252f3e')) < html.indexOf(encodeURIComponent('#5288c8')));
  assert.equal((String(Icons.both('Nobody', 'VM')).match(/<img /g) || []).length, 1);
  assert.equal(String(Icons.both('Nobody', 'BROWSER_SERVICE')), '');
});

test('dark theme: kind icons are recoloured with the same rule (dark neutral ink only)', () => {
  const {Icons, Theme} = page();
  Theme.set('dark', false, true);
  const vm = decode(Icons.kindUri('VM'));
  assert.ok(vm.includes('#5288c8') && vm.includes('#ffffff'));
  assert.equal(decode(Icons.kindUri('DARK')), KINDS.DARK.replace('#1a1a1a', VARS['--fg']));
});

test('rich() with kinds adds the k_<n> image styles and the formatter puts provider then kind before the text', () => {
  const {Icons, Theme} = page();
  Theme.set('light', false, true);
  const r = Icons.rich(['AWS', 'Azure', 'Nobody'], ['VM', 'BROWSER_SERVICE', 'VM']);
  assert.deepEqual(plain(Object.keys(r.rich)), ['i_0', 'i_1', 'k_0']);
  assert.equal(r.rich.k_0.backgroundColor.image, Icons.kindUri('VM'));
  assert.equal(r.formatter('a', 0), '{i_0|} {k_0|} a');
  assert.equal(r.formatter('b', 1), '{i_1|} b');
  assert.equal(r.formatter('c', 2), '{k_0|} c');
});

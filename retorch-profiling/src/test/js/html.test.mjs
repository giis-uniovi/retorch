/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {load} from './helpers/load.mjs';

const {Html} = load(['html.js'], ['Html']);
const hostile = `<"'&>`;

test('esc escapes the five HTML special characters, quotes included', () => {
  assert.equal(Html.esc(hostile), '&lt;&quot;&#39;&amp;&gt;');
  assert.equal(Html.esc(5), '5');
});

test('every interpolated value is escaped, in text and in attributes', () => {
  assert.equal(String(Html.t`<p title="${hostile}">${hostile}</p>`), '<p title="&lt;&quot;&#39;&amp;&gt;">&lt;&quot;&#39;&amp;&gt;</p>');
});

test('the static markup of the template is kept', () => {
  assert.equal(String(Html.t`<b>${'x'}</b>`), '<b>x</b>');
});

test('raw and nested templates are inserted as they are, but a string with markup is not', () => {
  assert.equal(String(Html.t`<i>${Html.raw('<b>ok</b>')}</i>`), '<i><b>ok</b></i>');
  assert.equal(String(Html.t`<i>${Html.t`<b>${'<'}</b>`}</i>`), '<i><b>&lt;</b></i>');
  assert.equal(String(Html.t`<i>${'<b>no</b>'}</i>`), '<i>&lt;b&gt;no&lt;/b&gt;</i>');
});

test('arrays are joined and null, undefined and false give nothing', () => {
  assert.equal(String(Html.t`<ul>${['a', '<'].map((v) => Html.t`<li>${v}</li>`)}</ul>`), '<ul><li>a</li><li>&lt;</li></ul>');
  assert.equal(String(Html.t`[${null}${undefined}${false}${0}]`), '[0]');
});

test('join uses the separator between fragments', () => {
  assert.equal(String(Html.join([Html.t`<a>${'1'}</a>`, Html.t`<a>${'<'}</a>`], ' ')), '<a>1</a> <a>&lt;</a>');
});

test('set writes the fragment inside the element', () => {
  const element = {innerHTML: ''};
  Html.set(element, Html.t`<p>${hostile}</p>`);
  assert.equal(element.innerHTML, '<p>&lt;&quot;&#39;&amp;&gt;</p>');
});

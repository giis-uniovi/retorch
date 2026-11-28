/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the alternatives card, with the filters (provider, type, region), the chart options (oversubscription, metric, sort) and the
   chart with the cost of every alternative shown. The controls are synchronised, never rebuilt unless their texts change, so a render
   does not take the keyboard focus away from the control that triggered it.
   vm: {providers: [{name, on}], kinds: [kind], regions: [region], filters: {kind, region}, showOver, metric, sort,
   rows: [{label, provider, used, over}] (the chart, in the order shown), ids: [alternative id] (same order), onSelect(id): click on a bar}. */
const AlternativesView = (function () {
  let root = null;

  function mount(rootEl) { root = rootEl; }

  function renderControls(vm) {
    const all = [['', I18n.t('filter.all')]];
    const boxes = View.$(root, 'providerBoxes');
    const key = JSON.stringify(vm.providers.map(function (p) { return p.name; })) + Theme.effective(); // the icons depend on the theme
    if (boxes.getAttribute('data-options') !== key) {
      Html.set(boxes, Html.join(vm.providers.map(function (p) {
        return Html.t`<label class="chk"><input type="checkbox" data-act="provider" value="${p.name}"> ${Icons.img(p.name)}${p.name}</label>`;
      }), ' '));
      boxes.setAttribute('data-options', key);
    }
    Array.prototype.forEach.call(boxes.querySelectorAll('input'), function (input, i) { input.checked = vm.providers[i].on; });
    View.syncSelect(View.$(root, 'kind'), all.concat(vm.kinds.map(function (k) { return [k, I18n.t('kind.' + k)]; })), vm.filters.kind);
    View.syncSelect(View.$(root, 'region'), all.concat(vm.regions.map(function (r) { return [r, r]; })), vm.filters.region);
    View.$(root, 'showOver').checked = vm.showOver;
    View.syncSelect(View.$(root, 'metric'), ['usd', 'pct'].map(function (m) { return [m, I18n.t('metric.' + m)]; }), vm.metric);
    View.syncSelect(View.$(root, 'sort'), ['cost', 'provider', 'over'].map(function (k) { return [k, I18n.t('sort.' + k)]; }), vm.sort);
  }

  function renderChart(vm) {
    Charts.alternatives(vm.rows, {showOver: vm.showOver, pct: vm.metric === 'pct', onClick: function (index) { vm.onSelect(vm.ids[index]); }});
  }

  function render(vm) {
    renderControls(vm);
    renderChart(vm);
  }

  function resize() { Charts.resize('compare'); }

  return {mount: mount, render: render, renderChart: renderChart, resize: resize};
})();

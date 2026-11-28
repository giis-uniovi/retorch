/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the comparison of the selected alternatives, stacked by phase or by TJob, with its data table.
   vm: {count: number of alternatives compared, group: 'phase'|'tjob', labels: [label of every alternative], keys: [phase or TJob],
   values: {key: [cost of every alternative]}} (the keys without any cost are not drawn, the colours follow the position of the key). */
const CompareView = (function () {
  let root = null;

  function mount(rootEl) { root = rootEl; }

  function stacks(vm) {
    const byPhase = vm.group === 'phase';
    const colors = Theme.colors();
    return vm.keys.map(function (k, n) {
      return {name: byPhase ? I18n.t('phase.' + k) : k, color: byPhase ? colors.phase[k] : Fmt.cycleColor(n),
        pattern: byPhase ? Fmt.PHASE_PATTERNS[k] : Charts.cyclePattern(n), values: vm.values[k]};
    }).filter(function (s) { return s.values.some(function (v) { return v > 0; }); });
  }

  function render(vm) {
    Html.set(View.$(root, 'cmpTabs'), View.tabs('cmp-group', vm.group, [['phase', 'cmp.byPhase'], ['tjob', 'cmp.byTjob']]));
    View.$(root, 'cmpCount').textContent = I18n.t('cmp.selected', {n: vm.count});
    const shown = stacks(vm);
    const fonts = Charts.fonts('cmpChart');
    Charts.setHeight('cmpChart', Math.max(fonts.root * 14, vm.labels.length * fonts.root * 2.4 + fonts.root * 6));
    Charts.stacked('cmpChart', {categories: vm.labels, stacks: shown, horizontal: true,
      description: I18n.t('cmp.title') + '. ' + I18n.t('desc.cmp', {n: vm.count})});
    View.stackTable('cmpData', I18n.t('cmp.title'), I18n.t('table.alt'), vm.labels, shown);
  }

  function resize() { Charts.resize('cmpChart'); }

  return {mount: mount, render: render, resize: resize};
})();

/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the breakdown of the selected alternative: key figures, stacked bars (TJob x phase, or phase x cloud object) with their
   tabs, and the donut charts by phase and by cloud object, each with its data table.
   vm: {alt, label, (the provider of alt gives the icon of the title) over: oversubscription cost, group: 'phase'|'object' (the tab), phases: [{key, cost}] (only the ones with cost),
   objects: [{key, cost}], categories: [key] and stackKeys: [key] of the bars, values: [[cost per category] per stack key]}. */
const DetailView = (function () {
  let root = null;

  function mount(rootEl) { root = rootEl; }

  const phaseName = function (p) { return I18n.t('phase.' + p); };

  function kpis(vm) {
    const a = vm.alt;
    Html.set(View.$(root, 'detailKpis'), [
      [I18n.t('detail.total'), Fmt.usd(a.total)], [I18n.t('detail.used'), Fmt.usd(a.total - vm.over)],
      [I18n.t('detail.over'), Fmt.usd(vm.over) + (a.total ? ' (' + Fmt.num(100 * vm.over / a.total, 0) + '%)' : '')],
      [I18n.t('detail.price'), a.kind === 'VM' ? Fmt.usd(a.hourlyPrice) + ' ' + I18n.t('detail.perHour') : I18n.t('detail.perUse')]
    ].map(function (k) { return View.kpiCard(k[0], k[1]); }));
  }

  function pies(vm) {
    const colors = Theme.colors();
    const entries = [['byPhase', 'detail.byPhase', vm.phases.map(function (e) {
      return {name: phaseName(e.key), value: e.cost, color: colors.phase[e.key], pattern: Fmt.PHASE_PATTERNS[e.key]};
    })], ['byObject', 'detail.byObject', vm.objects.map(function (e, n) {
      return {name: I18n.t('obj.' + e.key), value: e.cost, color: colors.object[e.key], pattern: Charts.cyclePattern(n * 3)};
    })]];
    entries.forEach(function (p) {
      const total = p[2].reduce(function (s, e) { return s + e.value; }, 0);
      Charts.pie(p[0], p[2], I18n.t(p[1]));
      Charts.dataTable(p[0] + 'Data', I18n.t(p[1]), [I18n.t('data.metric'), I18n.t('data.usd'), '%'],
        p[2].map(function (e) { return [e.name, Fmt.usd(e.value), Fmt.num(total ? 100 * e.value / total : 0, 1)]; }));
    });
  }

  /* Stacked bars: X = TJob stacked by phase, or X = phase stacked by cloud object. */
  function breakdown(vm) {
    const byPhase = vm.group === 'phase';
    const colors = Theme.colors();
    const stacks = vm.stackKeys.map(function (s, n) {
      return {
        name: byPhase ? phaseName(s) : I18n.t('obj.' + s), color: byPhase ? colors.phase[s] : colors.object[s],
        pattern: byPhase ? Fmt.PHASE_PATTERNS[s] : Charts.cyclePattern(n * 3), values: vm.values[n]
      };
    });
    const labels = byPhase ? vm.categories : vm.categories.map(phaseName);
    const title = I18n.t(byPhase ? 'detail.tabPhase' : 'detail.tabObject');
    Charts.stacked('byTjob', {categories: labels, stacks: stacks, horizontal: false, description: title + '. ' + I18n.t('desc.breakdown')});
    View.stackTable('byTjobData', title, I18n.t(byPhase ? 'data.tjob' : 'detail.byPhase'), labels, stacks);
  }

  function render(vm) {
    if (!vm.alt) { return; }
    Html.set(View.$(root, 'detailTitle'), Html.t`${Icons.img(vm.alt.provider)}${vm.label} (${vm.alt.region})`);
    kpis(vm);
    pies(vm);
    breakdown(vm);
    Html.set(View.$(root, 'tabs'), View.tabs('group', vm.group, [['phase', 'detail.tabPhase'], ['object', 'detail.tabObject']]));
  }

  function resize() { ['byTjob', 'byPhase', 'byObject'].forEach(Charts.resize); }

  return {mount: mount, render: render, resize: resize};
})();

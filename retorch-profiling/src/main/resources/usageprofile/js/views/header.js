/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the header (SUT and branch, subtitle, language and theme selectors) and the summary under it (warnings and key figures).
   vm: {lang, theme, plan, pricesFetchedAt, sut: {name, url, branch}, warnings: [text], kpis: {durationSec, peakCpu, peakMemGb, peakDiskGb,
   alternatives}}. */
const HeaderView = (function () {
  let head = null;
  let summary = null;

  function mount(headRoot, summaryRoot) {
    head = headRoot;
    summary = summaryRoot;
  }

  /* SUT name linked to its repository (only an https address is ever linked) and branch, when the execution knows them. */
  function renderFacts(sut) {
    const facts = [];
    if (sut.name) {
      const link = /^https:\/\//.test(sut.url)
        ? Html.t`<a href="${sut.url}" target="_blank" rel="noopener noreferrer">${sut.name}<span class="vh"> ${I18n.t('head.newTab')}</span></a>`
        : sut.name;
      facts.push(Html.t`<span class="fact"><span class="fact-k">${I18n.t('head.sut')}</span> ${link}</span>`);
    }
    if (sut.branch) { facts.push(Html.t`<span class="fact"><span class="fact-k">${I18n.t('head.branch')}</span> <code>${sut.branch}</code></span>`); }
    const box = View.$(head, 'facts');
    Html.set(box, facts);
    box.hidden = !facts.length;
  }

  function render(vm) {
    View.syncSegment(View.$(head, 'langGroup'), vm.lang);
    View.syncSegment(View.$(head, 'themeGroup'), vm.theme);
    renderFacts(vm.sut);
    View.$(head, 'sub').textContent = I18n.t('app.subtitle', {plan: vm.plan, date: Fmt.date(vm.pricesFetchedAt)});
    document.title = I18n.t('app.title') + (vm.sut.name ? ' · ' + vm.sut.name : '') + (vm.plan ? ' · ' + vm.plan : '');
    Html.set(View.$(summary, 'warnings'), vm.warnings.map(function (w) { return Html.t`<div class="warn">${w}</div>`; }));
    const k = vm.kpis;
    const cards = [['kpi.duration', Fmt.dur(k.durationSec)], ['kpi.peakCpu', Fmt.num(k.peakCpu, 2) + ' vCPU'], ['kpi.peakMem', Fmt.num(k.peakMemGb, 2) + ' GB'],
      ['kpi.peakDisk', Fmt.num(k.peakDiskGb, 2) + ' GB', I18n.t('kpi.peakDiskHint')], ['kpi.alternatives', k.alternatives]];
    Html.set(View.$(summary, 'kpis'), cards.map(function (c) { return View.kpiCard(I18n.t(c[0]), c[1], c[2]); }));
  }

  return {mount: mount, render: render};
})();

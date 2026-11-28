/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the table of all the alternatives (shown by the filters, in the chosen order) with the checkbox that adds each one to the
   comparison and the selection shortcuts. Wide: a table with sticky header. Narrow (container query in the CSS): one card per
   alternative, built from data-label.
   vm: {rows: [{id, label, kind, region, total, used, over, picked: selected alternative, compared: ticked}]}. */
const TableView = (function () {
  let root = null;

  function mount(rootEl) { root = rootEl; }

  function render(vm) {
    const cols = [['table.alt'], ['table.type'], ['table.region'], ['table.total'], ['table.used'], ['table.over']];
    const head = Html.t`<caption id="tableCaption" class="vh">${I18n.t('table.title')}</caption><thead><tr><th scope="col"><span class="vh">${I18n.t('cmp.pick')}</span></th>${
      cols.map(function (c) { return Html.t`<th scope="col">${I18n.t(c[0])}</th>`; })}</tr></thead>`;
    const cell = function (key, text) { return Html.t`<td data-label="${I18n.t('table.' + key)}">${text}</td>`; };
    const rows = vm.rows.map(function (r) {
      return Html.t`<tr class="alt ${r.picked ? 'sel' : ''}" data-id="${r.id}"><td class="c-pick"><label class="cell-check"><input type="checkbox" data-act="pick" data-cmp="${r.id}" aria-label="${I18n.t('cmp.pick') + ' ' + r.label}"${
        r.compared ? View.CHECKED : ''}></label></td><th scope="row"><button type="button" class="link" data-act="select-alt" data-select="${r.id}"${
        r.picked ? Html.raw(' aria-current="true"') : ''}>${r.label}</button></th>${cell('type', I18n.t('kind.' + r.kind))}${cell('region', r.region)}${
        cell('total', Fmt.usd(r.total))}${cell('used', Fmt.usd(r.used))}${cell('over', Fmt.usd(r.over))}</tr>`;
    });
    Html.set(View.$(root, 'table'), Html.t`${head}<tbody>${rows}</tbody>`);
    Html.set(View.$(root, 'cmpHelpers'), Html.join([['all', 'cmp.all'], ['none', 'cmp.none'], ['cheapest', 'cmp.cheapest']].map(function (h) {
      return Html.t`<button type="button" data-act="helper" data-helper="${h[0]}">${I18n.t(h[1])}</button>`;
    }), ' '));
  }

  return {mount: mount, render: render};
})();

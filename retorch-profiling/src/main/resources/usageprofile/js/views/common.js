/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Pieces shared by the views: small HTML builders and the idempotent control synchronisation. A view never reads the state: it
   receives a view-model, writes only inside its own root element and puts what the user did in data-act attributes, which the
   controller turns into changes of the state (controller.js). */
const View = (function () {
  const SELECTED = Html.raw(' selected');
  const CHECKED = Html.raw(' checked');

  /* The element with this id inside the root of a view. */
  function $(root, id) { return root.querySelector('#' + id); }

  function option(value, text, current) {
    return Html.t`<option value="${value}"${value === current ? SELECTED : ''}>${text}</option>`;
  }

  /* Pressed-state buttons (tabs). items: [[value, i18n key]]; act: the action the controller runs. */
  function tabs(act, current, items) {
    return Html.join(items.map(function (t) {
      return Html.t`<button type="button" data-act="${act}" data-v="${t[0]}" class="${current === t[0] ? 'on' : ''}" aria-pressed="${String(current === t[0])}">${I18n.t(t[1])}</button>`;
    }), ' ');
  }

  function kpiCard(name, value, hint) {
    return Html.t`<div class="card kpi"><dt>${name}</dt><dd>${value}</dd>${hint ? Html.t`<dd class="hint">${hint}</dd>` : ''}</div>`;
  }

  /* Fills a <select> from [[value, text]]. The options are rebuilt only when they changed (another language), so a render never
     takes the keyboard focus away from a select the user is using; the selection is always synchronised. */
  function syncSelect(select, options, current) {
    const key = JSON.stringify(options);
    if (select.getAttribute('data-options') !== key) {
      Html.set(select, options.map(function (o) { return option(o[0], o[1], current); }));
      select.setAttribute('data-options', key);
    }
    select.value = current;
  }

  /* Checks the radio with this value and moves the sliding indicator of the segmented control. */
  function syncSegment(group, current) {
    Array.prototype.forEach.call(group.querySelectorAll('input'), function (input, index) {
      input.checked = input.value === current;
      if (input.checked) { group.style.setProperty('--i', index); }
    });
  }

  /* Data table of a stacked chart: one row per category, one column per stack. */
  function stackTable(id, caption, headLabel, categories, stacks) {
    Charts.dataTable(id, caption, [headLabel].concat(stacks.map(function (s) { return s.name; })),
      categories.map(function (c, i) { return [c].concat(stacks.map(function (s) { return Fmt.usd(s.values[i]); })); }));
  }

  return {CHECKED: CHECKED, SELECTED: SELECTED, $: $, option: option, tabs: tabs, kpiCard: kpiCard, syncSelect: syncSelect, syncSegment: syncSegment,
    stackTable: stackTable};
})();

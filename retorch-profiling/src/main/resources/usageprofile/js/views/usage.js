/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* View: the measured usage of the execution (CPU, memory and disk over time) with its data table. It shares the time axis (range,
   plot margins and zoom) with the execution graph (see Charts.timeFrame). vm: {timeline: {t, cpu, memGb, diskGb}}. */
const UsageView = (function () {
  let root = null;

  function mount(rootEl) { root = rootEl; }

  function render(vm) { Charts.usage(vm.timeline); }

  function resize() { Charts.resize('usage'); }

  return {mount: mount, render: render, resize: resize, root: function () { return root; }};
})();

# Plan: per-container analysis in the TJob panel + projection simulator (front-end, agent F2)

Prerequisites already in place: the JSON has `graph.nodes[].containers[]` (see `usageprofile-containers.md`) and
`alternatives[].reservations[]` (see `usageprofile-next.md`). Plain ES2015, no build step, no external requests; GIIS header
comment on every file; same rules as the other front-end agents (read `dashboard-frontend.md`, `usageprofile-next.md`, `CLAUDE.md`
and Google's guide at `C:\Users\augus\AppData\Local\Temp\mwg-guide-41360-1795811341814.md`). You own ONLY
`retorch-profiling/src/main/resources/usageprofile/{usageprofile.html,usageprofile.css,js/*.js}`; a Java agent will add `js/simulator.js` to
the assembled list afterwards (list order: i18n, format, theme, charts, graph, simulator, app) so put the simulator code in
`js/simulator.js` (namespace `Simulator`) and its styles in `usageprofile.css`; the lead updates the Java side. Every new string in
es/en/fr/de. Accessibility and responsive rules from F1 apply (labels, fieldset/legend, focus-visible, target sizes, live regions,
no information by colour alone, container queries, fluid rem font sizes, keyboard path for everything, charts with text alternatives).

## A. Per-container analysis when a TJob is selected
In the side panel of the selected TJob (graph) add a section "Resources of this TJob" (`graph.containers`):
- A table with one row per container (`node.containers`): name, image, CPU peak / average (vCPU), memory peak / average (GB),
  storage split into writable layer / image / volumes and the total (`storageGb`). Numbers formatted with `Fmt`, in the selected language.
- A compact chart (ECharts) of the storage split per container (stacked layer + image + volumes) or CPU/memory bars: pick the clearest,
  readable at 320 px, with a text alternative (the table is the alternative).
- A sentence with the TJob totals (sum of peaks is NOT the peak of the sum: label the totals correctly, or show only per container).
- Empty state when `containers` is empty ("no per-container data in this file").
- The storage KPI/tooltips elsewhere mention that images and volumes are included.

## B. Projection simulator (new section "Projection")
Goal (user): simulate execution patterns, how many times the suite runs per day, and project the usage with reservations and discounts.
Inputs (a form with real `<label>`s, `fieldset`/`legend` for the radio group, numeric inputs with min/max, defaults):
- runs per day (default 4), days per month (default 22);
- pattern (radio): `perRun` = VM/container exists only while the suite runs (pay per execution); `always` = VM always on (24x7,
  730 h/month); `hours` = VM on N hours per day (default 10) on the given days (the disk stays all month);
- extra discount % on the on-demand compute price (default 0; covers spot / negotiated discounts);
- purchase option (select): On-demand + one entry per distinct (termYears, payment) found in `alternatives[].reservations`
  (labels such as "Reserved 1 year, no upfront"); reservations are 24x7 commitments, so they apply to `always` and `hours`.
Per alternative (VM or container) with `perRunCost = alt.total` (measured cost of one execution), `storageRun` = sum of the `storage`
items, `computeRun = perRunCost - storageRun`, `T = durationSec` of the execution, `H = 730`:
- `perRun`: monthly = runs * days * (computeRun * (1 - discount) + storageRun). Reservations do not apply.
- `always` (VM only): on-demand monthly = hourlyPrice*(1-discount)*H + (storageRun/T)*3600*H; reserved monthly =
  effectiveHourly*H + the same storage term.
- `hours` (VM only): on-demand = hourlyPrice*(1-discount)*hoursPerDay*days + storage term for all H hours; reserved = as `always`.
- Container services: always `perRun` (pay per use); show them in every pattern with a note.
- Utilisation of a VM in `always`/`hours`: busy hours per day = runs*T/3600 over the available hours per day (24 or N); more than
  100 % means the suite does not fit: warn clearly (and keep numbers). `annual = monthly * 12`.
Outputs (all recomputed live, no reload; a polite live region announces the cheapest alternative):
1. A results table (caption, `th scope`, sortable by cost optionally): alternative, monthly cost, annual cost, utilisation, and the
   cheapest highlighted with a non-colour cue.
2. **Break-even**: for each VM alternative with the chosen reservation, the runs/day from which reserving (always-on) is cheaper than
   paying per execution on demand: `runsPerDay* = reservedMonthly / (days * perRunOnDemandCost)`; show also the maximum runs/day the VM
   can run (`floor(24*3600 / T)`), and say "does not pay off before it is full" when `runsPerDay*` exceeds it.
3. A line chart **monthly cost vs runs per day** (x from 1 to a sensible maximum such as min(capacity, 100)): for the cheapest four
   alternatives (or the ones selected in the compare section) the on-demand per-run line (rising), the on-demand always-on line (flat)
   and the reserved always-on line (flat); a vertical marker at the current runs/day; text alternative = the break-even table.
Deep links: `#runs=&days=&pattern=perRun|always|hours&hours=&disc=&buy=<termYears>-<payment>|od` read at start and written when inputs
change. If no alternative has `reservations` (price cache from an older version) show an info note and offer only on-demand.

## How to test
Build the page with a Python assembler in the scratchpad (never in the repo) using the real data embedded in
`scratchpad\real\perfil5.html` (re-generate it with the CLI jar if needed; the JSON has `reservations` once the Java side of the
reservations is in the jar) and render with headless Edge via the DevTools protocol (scripts `cdp_click.js`, `cdp_hover.js`, `axe*.js`
in the scratchpad). Check es/en/fr/de, both themes, widths 320 and 1440, keyboard-only use of the form, axe-core with zero violations,
no JavaScript exceptions, and verify the formulas by evaluating `Simulator` functions with known inputs in the page (write the numbers
you checked in your report).
Final report (short): what changed, what you verified and how, what you could not verify.

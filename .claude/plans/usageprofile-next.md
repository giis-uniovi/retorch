# Plan: usage profile polish + reservation prices + projection simulator

The page and its files are called **usage profile** (output `usageprofile.html`); the Java package/resource directory are still
named `dashboard` internally and are renamed by the lead at the end. Do not rename anything yourself.

## Phase 1 (parallel, disjoint files)
| Agent | Owns | Task |
|---|---|---|
| J2 (Java) | `retorch-profiling/src/main/java/**`, Java tests and test data, `.claude/specs/profiling/**` | Real reservation prices (below) |
| F1 (front-end) | `retorch-profiling/src/main/resources/usageprofile/{usageprofile.html, usageprofile.css, js/*.js}` | Selectors redesign, accessibility (several audit passes), responsive layout, fluid fonts |

## Phase 2 (after phase 1, one agent): projection simulator in the page (uses the reservation data below).

## Contract: reservation prices (J2 produces, phase 2 consumes)
JSON `alternatives[]` gets one more property: `reservations`, a list (empty for container services and for VMs without data):
`{ "termYears": 1 | 3, "payment": "NO_UPFRONT" | "ALL_UPFRONT", "effectiveHourly": <USD per hour> }`
- `effectiveHourly` is the hourly price you really pay for a reserved VM: for NO_UPFRONT the hourly rate of the reservation; for
  ALL_UPFRONT the upfront fee divided by the hours of the term (`termYears * 8760`). It is what is paid 24x7, running or not.
- Azure (public Retail Prices API, `priceType eq 'Reservation'`, `reservationTerm` "1 Year" / "3 Years", total price of the term
  in `retailPrice`) -> ALL_UPFRONT only. Verify the real shape with curl before coding (like the existing on-demand parser).
- AWS (the EC2 bulk file already streamed for on-demand; section `terms.Reserved`): keep only `OfferingClass` = standard,
  `LeaseContractLength` 1yr / 3yr, `PurchaseOption` "No Upfront" (hourly dimension, unit `Hrs`) and "All Upfront" (dimension with
  unit `Quantity` = upfront fee). Linux, shared tenancy, as the existing on-demand filter. Keep memory low (streaming).
- Fargate / Azure Container Instances: no reservations (`[]`).
- Spot prices are out of scope.

## Phase 1 requirements

### J2 (Java, Java 8, Sonar-clean: methods < 15 cognitive complexity, no duplicated literals, <= 7 parameters)
1. `PriceCatalog.VmOffer` gets a list of reservation options; add a `schemaVersion` to `PriceCatalog`; a cache with another version
   (or none) must be ignored and refetched.
2. Parse Azure and AWS reservations as described, in `PricingFetcher` (split into small classes if it grows). Tests with JSON
   fixtures (no network in tests). Check the Azure shape with curl; for AWS write the fixture from the structure of the real file
   (you can fetch the first ~2 MB of `https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/eu-west-1/index.json`
   to look at the real `terms.Reserved` structure; do NOT download the whole 450 MB).
3. New DTO `dto/ReservationOption` (termYears, payment, effectiveHourly); `dto/Alternative` carries `reservations` (immutable list);
   `CostCalculator` copies the reservations of the chosen VM into its alternative.
4. JSON property names exactly as in the contract; the existing properties do not change. A test checks the new property.
5. Update `.claude/specs/profiling/usageprofile.md` (and `main.md` if needed) per the iron rule in CLAUDE.md.
Do not run the module-wide Maven build while F1 edits the resources? It does not matter for Java: you may run
`mvn -q -pl retorch-profiling -am test` (F1 does not touch Java). Do not touch `src/main/resources`.

### F1 (front-end; plain ES2015, no build step, no external requests)
Read first: `.claude/plans/dashboard-frontend.md` (the original contract: files, placeholders, JSON), `CLAUDE.md`, the guide
`C:\Users\augus\AppData\Local\Temp\mwg-guide-41360-1795811341814.md` (Google's modern-web-guidance: responsive-design, accessibility,
responsive-table, form-factor-adaptation, motion; follow it) and the current files. Do not change the JSON contract or the placeholders.
1. **Language and theme selectors**: replace the two plain `<select>`s by attractive, compact segmented controls. Language: a pill
   group `ES | EN | FR | DE` (the current one highlighted by a sliding indicator). Theme: three icon buttons (sun = light, moon =
   dark, monitor = auto) with inline SVG icons (no external assets). Implement them as native radio inputs styled as segmented
   buttons inside `role="radiogroup"` with an accessible name (so arrow keys and screen readers work for free), `:focus-visible`
   rings, tooltips through `title` + `aria-label`, text hidden on very narrow screens but the control kept at least 44 px high on
   `pointer: coarse` and 24 px otherwise. Animations only when `prefers-reduced-motion: no-preference`. They must look good in
   both themes and keep working with the deep links (`#lang=`, `#theme=`).
2. **Accessibility, at least three audit passes**, each followed by fixes, and report the result of every pass:
   - Automated: install `axe-core` into the scratchpad (`npm install axe-core` in a scratch directory, never in the repo), inject
     `node_modules/axe-core/axe.min.js` through the DevTools protocol (the scripts `cdp_click.js`/`cdp_hover.js` in the scratchpad
     show how to drive Edge from Node 26 with its global WebSocket) and run `axe.run()` in BOTH themes, in all four languages, with
     a node selected and without. Fix every violation (contrast included).
   - Manual (keyboard and semantics) following the guide: landmarks (`header`, one `main`, `footer`), skip link, heading order (one
     h1, no skipped levels), `lang` on `<html>` updated when the language changes, translated `<title>`, accessible names for every
     control and icon button, `:focus-visible` styles with >= 3:1 contrast, target sizes, tables with `caption`/`th scope`,
     scrollable regions with `tabindex="0"` + `role="region"` + name, live region for the side panel updates (polite), no
     information by colour alone (the three phases need a non-colour cue too, e.g. patterns or labels), `forced-colors` support.
   - Charts: every chart container gets `role="img"` and an `aria-label`/description (use the ECharts `aria` option and/or a text
     summary) and the same data available as a semantic table or list. **The execution graph needs a keyboard path**: a list of
     buttons, one per TJob (id, stage, duration), that selects the TJob exactly like clicking its bar (this also helps touch users);
     Enter/Space activate; after selecting, focus moves to the panel heading; Escape closes the panel and returns focus.
   - Tab through the whole page with the DevTools protocol (dispatch Tab key events and record `document.activeElement`) and check
     the order is logical and nothing is a keyboard trap.
3. **Responsive layout**: verify with Edge through the DevTools protocol (`Emulation.setDeviceMetricsOverride`, mobile true) at
   widths 320, 360, 390, 768, 1024 and 1440: no horizontal page scroll (`document.documentElement.scrollWidth <= innerWidth`
   checked programmatically), nothing clipped, the graph side panel stacks under the chart, filters/controls wrap, tables scroll
   inside their own labelled region or become stacked cards with container queries (see `responsive-table`), charts resize on
   rotation. Take screenshots (Page.captureScreenshot) and LOOK at them. Use container queries for components and `dvh/dvw` instead
   of `vh/vw`.
4. **Fluid font sizes**: sizes in `rem`, root font size left to the user's browser setting, fluid `clamp()` scales for the title,
   headings and KPI numbers (maximum no more than 2.5x the minimum), body text never below 16 px equivalent; the ECharts texts
   (axis labels, legend, tooltip, graph labels) must also adapt: derive their pixel sizes from the computed root font size and the
   container width, and re-render on resize/orientation change (debounced). Check at 200 % browser zoom / 320 px width (WCAG 1.4.4,
   1.4.10).
5. **Bug to verify** (already fixed, confirm and keep fixed): clicking a TJob bar must open the panel with its tests; the previous
   bug was `chart.getZr().off('click')` without a handler, which removes ECharts' own click dispatch. Also make sure that hovering and
   clicking never throws a JavaScript exception: run `cdp_click.js` (prints `problems`) and `cdp_hover.js`; in `cdp_click.js` ignore
   the exceptions that come from its own debug instrumentation if any, but fix real ones.
6. Keep all existing features working (scenario explorer, compare chart, filters, deep links, i18n for es/en/fr/de: every new
   string in all four languages).

How to test: build the page with a Python assembler in the scratchpad (placeholders in the original contract) using, as data, the
JSON embedded in `C:\Users\augus\AppData\Local\Temp\claude\9b566184736217ee\6101f171-fa1e-4f81-8ed5-aeb133495985\scratchpad\real\perfil3.html`
(real Jenkins run, 8 alternatives, 10-node graph with tests; extract it from the `<script type="application/json" id="data">` tag).
Do NOT run Maven, do not touch Java, do not use git.

## Rules for every agent
- Touch only your files; never use git; no commits. Use the Write/Edit tools for file contents (long bash heredocs lose backslashes
  in this environment) and be careful with escapes when writing through Python.
- Final report (short): what changed, what you verified and how (numbers: violations before/after each audit pass), what you could not.

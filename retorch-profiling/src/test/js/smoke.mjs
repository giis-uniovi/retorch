/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* Headless-browser smoke test of the usage profile page (Chrome or Edge through the DevTools protocol, no dependency but axe-core).
 *
 *   node smoke.mjs [page.html] [--shots <dir>] [--full]
 *
 * The page is the one written by UsageProfilePageTests (target/usageprofile-smoke/usageprofile.html, run `mvn test` first).
 * Checks: no JavaScript error or console.error, every section renders, axe-core finds 0 violations, the usage chart and the
 * execution graph have the same plot area and linked zoom, the deep link round-trips, the keyboard focus survives a re-render.
 * --full runs axe in the 4 languages and both themes (default: en, both themes); --shots saves screenshots (light and dark,
 * wide and narrow) to compare two versions of the page. Environment: BROWSER (path of the executable), AXE_CORE (path of
 * axe.min.js), SMOKE_REQUIRE=1 (fail instead of skipping when there is no browser).
 * Needs Node 22 or later (global fetch and WebSocket). */
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flag = (name) => args.indexOf(name) >= 0;
const option = (name) => (args.indexOf(name) >= 0 ? args[args.indexOf(name) + 1] : null);
const positional = args.filter((a, i) => !a.startsWith('--') && args[i - 1] !== '--shots');
const pagePath = path.resolve(positional[0] || path.join(here, '../../../target/usageprofile-smoke/usageprofile.html'));
const shotsDir = option('--shots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const failures = [];
const check = (ok, message) => { console.log((ok ? 'ok   ' : 'FAIL ') + message); if (!ok) { failures.push(message); } };

function findBrowser() {
  const candidates = [process.env.BROWSER,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'];
  return candidates.find((c) => c && fs.existsSync(c)) || null;
}

function findAxe() {
  const candidates = [process.env.AXE_CORE, path.join(here, 'node_modules/axe-core/axe.min.js')];
  try { candidates.push(createRequire(import.meta.url).resolve('axe-core/axe.min.js')); } catch (e) { /* not installed */ }
  const found = candidates.find((c) => c && fs.existsSync(c));
  return found ? fs.readFileSync(found, 'utf8') : null;
}

/* Minimal DevTools protocol client: a headless browser with one page. */
async function launch(executable) {
  const port = 9300 + Math.floor(Math.random() * 600);
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'retorch-smoke-'));
  const child = spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=' + port,
    '--user-data-dir=' + profile, 'about:blank'], {stdio: 'ignore'});
  let list = null;
  for (let i = 0; i < 80 && !list; i++) {
    try { list = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json(); } catch (e) { await sleep(250); }
  }
  if (!list) { child.kill(); throw new Error('the browser did not start'); }
  const ws = new WebSocket(list.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve) => { ws.onopen = resolve; });
  let id = 0;
  const pending = new Map();
  const problems = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    if (msg.method === 'Runtime.exceptionThrown') {
      const d = msg.params.exceptionDetails;
      problems.push('exception: ' + ((d.exception && d.exception.description) || d.text));
    }
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
      problems.push('console.error: ' + msg.params.args.map((a) => a.value || a.description).join(' '));
    }
  };
  const send = (method, params) => new Promise((resolve) => {
    const i = ++id;
    pending.set(i, resolve);
    ws.send(JSON.stringify({id: i, method, params: params || {}}));
  });
  const ev = async (expression) => {
    const r = await send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (r.result.exceptionDetails) { throw new Error('evaluating ' + expression.slice(0, 80) + ': ' + JSON.stringify(r.result.exceptionDetails.exception && r.result.exceptionDetails.exception.description)); }
    return r.result.result.value;
  };
  await send('Page.enable');
  await send('Runtime.enable');
  return {
    problems, ev, send,
    view: (width, height) => send('Emulation.setDeviceMetricsOverride', {width, height, deviceScaleFactor: 1, mobile: false}),
    /* Opens the page with this hash from scratch (a hash-only navigation would not reload it). */
    async open(url, wait) {
      await send('Page.navigate', {url: 'about:blank'});
      await send('Page.navigate', {url});
      for (let i = 0; i < 100; i++) {
        let ready = false;
        try { ready = await ev("document.readyState === 'complete' && !!document.querySelector('#usage canvas, #usage svg')"); } catch (e) { /* loading */ }
        if (ready) { break; }
        await sleep(100);
      }
      await sleep(wait || 400);
    },
    async shot(file) {
      const metrics = (await send('Page.getLayoutMetrics')).result;
      const size = metrics.cssContentSize || metrics.contentSize;
      const r = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true,
        clip: {x: 0, y: 0, width: size.width, height: Math.min(size.height, 16000), scale: 1}});
      fs.writeFileSync(file, Buffer.from(r.result.data, 'base64'));
    },
    close() { try { ws.close(); } catch (e) { /* closed */ } child.kill(); try { fs.rmSync(profile, {recursive: true, force: true}); } catch (e) { /* in use */ } }
  };
}

/* Pixel position of the plot area (x axis extent) of an ECharts instance, in page coordinates. */
const PLOT = `(function (id) {
  var c = echarts.getInstanceByDom(document.getElementById(id));
  var axis = c.getModel().getComponent('xAxis', 0).axis;
  var ext = axis.scale.getExtent();
  var box = document.getElementById(id).getBoundingClientRect();
  return {x0: Math.round(box.left + c.convertToPixel({xAxisIndex: 0}, ext[0])), x1: Math.round(box.left + c.convertToPixel({xAxisIndex: 0}, ext[1])), ext: ext.join('..')};
})`;
const zoomOf = (id) => `JSON.stringify(echarts.getInstanceByDom(document.getElementById(${JSON.stringify(id)})).getOption().dataZoom.map(function (z) { return [Math.round(z.start), Math.round(z.end)]; }))`;

async function aligned(b, label) {
  const usage = await b.ev(`JSON.stringify(${PLOT}('usage'))`);
  const graph = await b.ev(`JSON.stringify(${PLOT}('graphChart'))`);
  check(usage === graph, `plot areas of the usage chart and the execution graph are identical ${label} (${usage} / ${graph})`);
}

async function renders(b) {
  const counts = await b.ev(`JSON.stringify({
    kpis: document.querySelectorAll('#kpis .kpi').length, usage: !!document.querySelector('#usage canvas, #usage svg'),
    graph: !!document.querySelector('#graphChart canvas, #graphChart svg'), compare: !!document.querySelector('#compare canvas, #compare svg'),
    cmp: !!document.querySelector('#cmpChart canvas, #cmpChart svg'), byTjob: !!document.querySelector('#byTjob canvas, #byTjob svg'),
    pies: document.querySelectorAll('#byPhase canvas, #byObject canvas, #byPhase svg, #byObject svg').length,
    sim: !!document.querySelector('#simChart canvas, #simChart svg'), rows: document.querySelectorAll('#table tbody tr').length,
    simRows: document.querySelectorAll('#simTable tbody tr').length, beRows: document.querySelectorAll('#simBe tbody tr').length,
    tjobs: document.querySelectorAll('#tjobList button').length, title: document.title, h1: document.querySelector('h1').textContent,
    facts: document.getElementById('facts').textContent})`);
  const c = JSON.parse(counts);
  check(c.kpis === 5 && c.usage && c.graph && c.compare && c.cmp && c.byTjob && c.pies >= 2 && c.sim, 'every chart is drawn: ' + counts);
  check(c.rows > 3 && c.simRows > 3 && c.beRows > 0 && c.tjobs === 3, 'tables and the list of TJobs have rows');
  check(/petclinic/.test(c.facts) && /main/.test(c.facts), 'header shows the SUT and the branch');
  check(c.h1 === 'RETORCH Resource Usage Profile' && /RETORCH Resource Usage Profile/.test(c.title), 'title and h1 are app.title');
}

async function runAxe(b, axe, tags) {
  if (!axe) { return null; }
  await b.ev('document.querySelectorAll("details").forEach(function (d) { d.open = true; })');
  await b.ev(axe);
  const out = await b.ev('axe.run({runOnly: {type: "tag", values: ' + JSON.stringify(tags) + '}}).then(function (r) { return JSON.stringify(r.violations.map(function (v) { return {id: v.id, n: v.nodes.length, t: v.nodes.slice(0, 2).map(function (n) { return n.target.join(" "); })}; })); })');
  return JSON.parse(out);
}

async function main() {
  const executable = findBrowser();
  if (!executable) {
    console.log('SKIP: no Chrome or Edge found (set BROWSER)');
    process.exit(process.env.SMOKE_REQUIRE ? 1 : 0);
  }
  if (!fs.existsSync(pagePath)) { console.error('page not found: ' + pagePath + ' (run mvn test first)'); process.exit(1); }
  const axe = findAxe();
  if (!axe) { console.log('WARN axe-core not installed (npm install in src/test/js): the accessibility check is skipped'); }
  const base = pathToFileURL(pagePath).href;
  const b = await launch(executable);
  try {
    await b.view(1300, 1800);
    await b.open(base + '#lang=en&theme=light');
    check(b.problems.length === 0, 'no JavaScript errors while loading ' + JSON.stringify(b.problems));
    await renders(b);
    const source = fs.readFileSync(pagePath, 'utf8');
    check(/<title>RETORCH Resource Usage Profile<\/title>/.test(source) && /<h1 data-i18n="app.title">RETORCH Resource Usage Profile<\/h1>/.test(source), 'the static title and h1 are app.title');
    check(await b.ev("document.getElementById('cmpCard').nextElementSibling.id === 'tableCard'"), 'the table of all the alternatives is right below the comparison');
    check(await b.ev("(function () { var a = document.querySelector('.footer-logo'); return a.getAttribute('rel') === 'noopener noreferrer' && a.textContent.indexOf('new tab') >= 0; })()"), 'the footer link has noopener noreferrer and the new tab note');
    await aligned(b, 'before selecting a TJob');

    // selecting a TJob keeps the axes aligned and opens the panel below the chart
    await b.ev("document.querySelector('#tjobList button').click()");
    await sleep(500);
    await aligned(b, 'after selecting a TJob');
    check(await b.ev("!document.getElementById('graphPanel').hidden && document.getElementById('graphPanel').getBoundingClientRect().top >= document.getElementById('graphChart').getBoundingClientRect().bottom"),
      'the TJob panel is shown below the chart');
    check(await b.ev("!!document.querySelector('#graphContChart canvas, #graphContChart svg')"), 'the chart per container is drawn');

    // linked zoom: zooming the usage chart zooms the graph and keeps the plot areas aligned
    await b.ev("echarts.getInstanceByDom(document.getElementById('usage')).dispatchAction({type: 'dataZoom', start: 20, end: 60})");
    await sleep(400);
    const zu = await b.ev(zoomOf('usage'));
    const zg = await b.ev(zoomOf('graphChart'));
    check(zu === zg && zu.indexOf('[20,60]') >= 0, 'zoom is linked between the usage chart and the graph ' + zu + ' / ' + zg);
    await aligned(b, 'after zooming');
    await b.ev("echarts.getInstanceByDom(document.getElementById('graphChart')).dispatchAction({type: 'dataZoom', start: 0, end: 100})");
    await sleep(300);
    check((await b.ev(zoomOf('usage'))) === (await b.ev(zoomOf('graphChart'))), 'zoom reset on the graph also resets the usage chart');

    // metric selector of the chart per container
    for (const metric of ['mem', 'cpu', 'storage']) {
      await b.ev("(function () { var r = document.querySelector('input[name=contMetric][value=" + metric + "]'); r.checked = true; r.dispatchEvent(new Event('change', {bubbles: true})); })()");
      await sleep(300);
      const names = await b.ev("echarts.getInstanceByDom(document.getElementById('graphContChart')).getOption().series.map(function (s) { return s.name + (s.stack ? '(stacked)' : ''); }).join(',')");
      check(metric === 'storage' ? /\(stacked\)/.test(names) : !/\(stacked\)/.test(names), `metric ${metric} draws ${names}`);
    }

    // selecting another alternative refreshes the cost panel only: the Gantt chart instance and the open containers stay as they are
    await b.ev("echarts.getInstanceByDom(document.getElementById('graphChart')).__smoke = 1; document.querySelector('#graphPanel details').open = true;");
    const costBefore = await b.ev("document.getElementById('graphCost').textContent");
    await b.ev("document.querySelector('#table [data-select]:not([aria-current])').click()");
    await sleep(300);
    check(await b.ev("echarts.getInstanceByDom(document.getElementById('graphChart')).__smoke === 1"), 'selecting an alternative does not re-create the execution graph');
    check(await b.ev("document.querySelector('#graphPanel details').open"), 'selecting an alternative keeps the open containers open');
    check((await b.ev("document.getElementById('graphCost').textContent")) !== costBefore, 'the cost of the TJob in the panel follows the selected alternative');

    // focus survives a re-render: a control of the table keeps the focus when the table is rebuilt
    await b.ev("document.querySelector('#table [data-select]').focus()");
    const before = await b.ev("document.activeElement.getAttribute('data-select')");
    await b.ev("document.querySelector('#cmpHelpers [data-helper=none]').focus(); document.querySelector('#cmpHelpers [data-helper=none]').click()");
    await sleep(300);
    check(await b.ev("document.activeElement && document.activeElement.getAttribute('data-helper') === 'none'"), 'focus stays on the helper button after re-rendering the table');
    await b.ev("document.querySelector('#cmpTabs [data-v=tjob]').focus(); document.querySelector('#cmpTabs [data-v=tjob]').click()");
    await sleep(300);
    check(await b.ev("document.activeElement && document.activeElement.getAttribute('data-v') === 'tjob'"), 'focus stays on the tab after re-rendering the comparison');
    await b.ev("document.querySelector('#table [data-select=\"" + String(before).replace(/"/g, '\\"') + "\"]').focus(); document.querySelector('#table [data-select=\"" + String(before).replace(/"/g, '\\"') + "\"]').click()");
    await sleep(300);
    check(await b.ev("document.activeElement && document.activeElement.hasAttribute('data-select')"), 'focus stays on the alternative after selecting it');

    // Escape closes the TJob panel
    await b.ev("document.querySelector('#tjobList button').focus()");
    await b.send('Input.dispatchKeyEvent', {type: 'rawKeyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27});
    await b.send('Input.dispatchKeyEvent', {type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27});
    await sleep(300);
    check(await b.ev("document.getElementById('graphPanel').hidden"), 'Escape closes the TJob panel');

    // deep link: the state of a link is restored and written back
    const alt = await b.ev("document.querySelectorAll('#table [data-select]')[2].getAttribute('data-select')");
    const link = '#lang=fr&theme=dark&node=tjobb&alt=' + encodeURIComponent(alt) + '&over=0&metric=pct&sort=provider&cmpgroup=tjob&runs=7&days=20&pattern=hours&hours=8&disc=10';
    await b.open(base + link);
    const restored = JSON.parse(await b.ev(`JSON.stringify({lang: document.documentElement.lang, theme: document.documentElement.getAttribute('data-theme'),
      node: (document.querySelector('#tjobList [aria-current]') || {getAttribute: function () { return null; }}).getAttribute('data-node'),
      alt: (document.querySelector('#table [data-select][aria-current]') || {getAttribute: function () { return null; }}).getAttribute('data-select'),
      over: document.getElementById('showOver').checked, metric: document.getElementById('metric').value, sort: document.getElementById('sort').value,
      cmpgroup: document.querySelector('#cmpTabs button.on').getAttribute('data-v'), runs: document.getElementById('simRuns').value,
      days: document.getElementById('simDays').value, pattern: document.querySelector('input[name=simPattern]:checked').value,
      hours: document.getElementById('simHours').value, disc: document.getElementById('simDisc').value})`));
    check(restored.lang === 'fr' && restored.theme === 'dark' && restored.node === 'tjobb' && restored.alt === alt && restored.over === false &&
      restored.metric === 'pct' && restored.sort === 'provider' && restored.cmpgroup === 'tjob' && restored.runs === '7' && restored.days === '20' &&
      restored.pattern === 'hours' && restored.hours === '8' && restored.disc === '10', 'a deep link restores the state ' + JSON.stringify(restored));
    const written = new URLSearchParams(await b.ev('location.hash.slice(1)'));
    const wanted = new URLSearchParams(link.slice(1));
    const same = [...wanted.keys()].every((k) => written.get(k) === wanted.get(k));
    check(same, 'the hash written by the page contains the state of the link: ' + written.toString());
    // every state key is written to the hash and read back: change everything by hand, reload the hash, compare the page
    const ui = async (selector, action) => { await b.ev(`(function () { var e = document.querySelector(${JSON.stringify(selector)}); ${action} })()`); await sleep(250); };
    await b.open(base + '#lang=en&theme=light');
    await ui('#table [data-select]:not([aria-current])', 'e.click();');
    await ui('#sort', "e.value = 'over'; e.dispatchEvent(new Event('change', {bubbles: true}));");
    await ui('#metric', "e.value = 'pct'; e.dispatchEvent(new Event('change', {bubbles: true}));");
    await ui('#showOver', "e.click();");
    await ui('#cmpHelpers [data-helper=all]', 'e.click();');
    await ui('#table [data-cmp]', 'e.click();');
    await ui('#cmpTabs [data-v=tjob]', 'e.click();');
    await ui('#tabs [data-v=object]', 'e.click();');
    await ui('#tjobList li:nth-child(2) button', 'e.click();');
    await ui('input[name=contMetric][value=cpu]', "e.checked = true; e.dispatchEvent(new Event('change', {bubbles: true}));");
    await ui('#providerBoxes label:last-child input', 'e.click();');
    await ui('#kind option:nth-child(2)', "e.parentNode.value = e.value; e.parentNode.dispatchEvent(new Event('change', {bubbles: true}));");
    await ui('#region option:last-child', "e.parentNode.value = e.value; e.parentNode.dispatchEvent(new Event('change', {bubbles: true}));");
    await ui('#simRuns', "e.value = '9'; e.dispatchEvent(new Event('input', {bubbles: true}));");
    await ui('#simPatHours', "e.click();");
    await ui('#simHours', "e.value = '6'; e.dispatchEvent(new Event('input', {bubbles: true}));");
    await ui('#simBuy', "e.selectedIndex = e.options.length - 1; e.dispatchEvent(new Event('change', {bubbles: true}));");
    await sleep(300);
    const snapshot = `JSON.stringify({lang: document.documentElement.lang, theme: document.documentElement.getAttribute('data-theme'),
      node: [].map.call(document.querySelectorAll('#tjobList [aria-current]'), function (e) { return e.getAttribute('data-node'); }),
      alt: [].map.call(document.querySelectorAll('#table [data-select][aria-current]'), function (e) { return e.getAttribute('data-select'); }),
      over: document.getElementById('showOver').checked, metric: document.getElementById('metric').value, sort: document.getElementById('sort').value,
      kind: document.getElementById('kind').value, region: document.getElementById('region').value,
      providers: [].map.call(document.querySelectorAll('#providerBoxes input'), function (e) { return e.checked; }),
      compared: [].map.call(document.querySelectorAll('#table [data-cmp]:checked'), function (e) { return e.getAttribute('data-cmp'); }),
      cmpgroup: document.querySelector('#cmpTabs button.on').getAttribute('data-v'), group: document.querySelector('#tabs button.on').getAttribute('data-v'),
      cont: (document.querySelector('input[name=contMetric]:checked') || {}).value, rows: document.querySelectorAll('#table tbody tr').length,
      runs: document.getElementById('simRuns').value, days: document.getElementById('simDays').value, pattern: document.querySelector('input[name=simPattern]:checked').value,
      hours: document.getElementById('simHours').value, disc: document.getElementById('simDisc').value, buy: document.getElementById('simBuy').value})`;
    const hashAfter = await b.ev('location.hash');
    const stateAfter = await b.ev(snapshot);
    check(/sort=over/.test(hashAfter) && /metric=pct/.test(hashAfter) && /group=object/.test(hashAfter) && /cont=cpu/.test(hashAfter) && /runs=9/.test(hashAfter) &&
      /pattern=hours/.test(hashAfter) && /over=0/.test(hashAfter) && /noprov=/.test(hashAfter) && /kind=/.test(hashAfter) && /region=/.test(hashAfter) &&
      /cmpgroup=tjob/.test(hashAfter) && /node=/.test(hashAfter) && /alt=/.test(hashAfter) && /buy=/.test(hashAfter), 'every change is written to the hash: ' + hashAfter);
    await b.open(base + hashAfter);
    check((await b.ev(snapshot)) === stateAfter, 'reopening the written link restores the same page: ' + stateAfter);
    check((await b.ev('location.hash')) === hashAfter, 'the link is stable after reopening');
    check(b.problems.length === 0, 'no JavaScript errors after the interactions ' + JSON.stringify(b.problems));

    // following another link in the same page (hashchange): what the link says is applied, the rest goes back to the default
    await b.open(base + '#lang=en&theme=light&node=tjoba&metric=pct');
    await b.ev("location.hash = '#lang=en&theme=light&sort=provider&node=tjobb'");
    await sleep(600);
    check(await b.ev("document.getElementById('sort').value === 'provider' && document.getElementById('metric').value === 'usd' && document.querySelector('#tjobList [aria-current]').getAttribute('data-node') === 'tjobb'"),
      'a hashchange applies the new link and resets what it does not say');
    check(b.problems.length === 0, 'no JavaScript errors after a hashchange ' + JSON.stringify(b.problems));

    // provider icons: next to every name of a provider, decorative, and the chart labels draw them in both themes
    for (const theme of ['light', 'dark']) {
      await b.open(`${base}#lang=en&theme=${theme}&node=tjoba&cmp=all`);
      const icons = JSON.parse(await b.ev(`JSON.stringify({
        known: Object.keys(JSON.parse(document.getElementById('icons').textContent)),
        providers: [].map.call(document.querySelectorAll('#providerBoxes label'), function (l) { return l.querySelectorAll('img.prov-icon').length; }),
        boxes: document.querySelectorAll('#providerBoxes input').length,
        rows: document.querySelectorAll('#table tbody tr').length, rowIcons: document.querySelectorAll('#table tbody tr th img.prov-icon').length,
        title: document.querySelectorAll('#detailTitle img.prov-icon').length, panel: document.querySelectorAll('#graphCost img.prov-icon, #graphPanel h4 img.prov-icon').length,
        alt: [].every.call(document.querySelectorAll('img.prov-icon'), function (i) { return i.getAttribute('alt') === '' && i.naturalWidth > 0; }),
        rowLabel: document.querySelector('#table tbody tr input[data-cmp]').getAttribute('aria-label'),
        labelRich: ['compare', 'cmpChart'].map(function (id) {
          var o = echarts.getInstanceByDom(document.getElementById(id)).getOption();
          var l = (o.yAxis[0].axisLabel || {});
          return Object.keys(l.rich || {}).length + ':' + (typeof l.formatter === 'function' ? String(l.formatter('x', 0)).indexOf('{i_') : 'none');
        })})`));
      check(icons.known.length > 0 && icons.known.indexOf('GCP') < 0, `the page embeds only the icons of its providers (${theme}): ${icons.known}`);
      check(icons.boxes > 0 && icons.providers.every((n) => n === 1), `every provider filter has an icon (${theme})`);
      check(icons.rows > 0 && icons.rowIcons === icons.rows, `every alternative of the table has an icon (${theme}): ${icons.rowIcons}/${icons.rows}`);
      check(icons.title === 1 && icons.panel === 1, `the title of the selected alternative and the cost heading of the TJob have an icon (${theme})`);
      check(icons.alt, `the icons are decorative (alt="") and load (${theme})`);
      check(/^Select /.test(icons.rowLabel) || icons.rowLabel.length > 0, `the checkbox keeps its text label (${theme}): ${icons.rowLabel}`);
      check(icons.labelRich.every((x) => /^[1-9]\d*:0$/.test(x)), `the labels of the Alternatives and Compare charts carry the icons (${theme}): ${icons.labelRich}`);
      await sleep(400);
      check(b.problems.length === 0, `no JavaScript errors drawing the icons (${theme}) ` + JSON.stringify(b.problems));
    }

    // accessibility
    const tags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'];
    if (axe) {
      for (const theme of ['light', 'dark']) {
        for (const lang of flag('--full') ? ['es', 'en', 'fr', 'de'] : ['en']) {
          await b.open(`${base}#lang=${lang}&theme=${theme}&node=tjoba`);
          const v = await runAxe(b, axe, tags);
          check(v.length === 0, `axe: 0 violations (${theme}, ${lang}) ${v.length ? JSON.stringify(v) : ''}`);
        }
      }
    }

    // a small resize only adapts the charts (they are not re-created); a breakpoint change draws them again; the axes stay aligned
    await b.view(1300, 1800);
    await b.open(base + '#lang=en&theme=light&node=tjoba');
    await b.ev("echarts.getInstanceByDom(document.getElementById('graphChart')).__smoke = 1");
    await b.view(1260, 1800);
    await sleep(700);
    check(await b.ev("echarts.getInstanceByDom(document.getElementById('graphChart')).__smoke === 1"), 'a small resize does not re-create the charts');
    await aligned(b, 'after a small resize');
    await b.view(700, 1800);
    await sleep(700);
    await aligned(b, 'after a large resize');
    check(await b.ev("document.getElementById('usage').getBoundingClientRect().width <= 700"), 'the charts follow a large resize');

    // narrow screen: no horizontal scroll and the axes still aligned
    await b.view(390, 900);
    await b.open(base + '#lang=en&theme=light&node=tjoba');
    check(await b.ev('document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1'), 'no horizontal page scroll at 390 px');
    await aligned(b, 'at 390 px');

    if (shotsDir) {
      fs.mkdirSync(shotsDir, {recursive: true});
      for (const [width, name] of [[1300, 'wide'], [390, 'narrow']]) {
        for (const theme of ['light', 'dark']) {
          await b.view(width, 900);
          await b.open(`${base}#lang=en&theme=${theme}&node=tjobb`, 800);
          await b.shot(path.join(shotsDir, `${name}-${theme}.png`));
        }
      }
      console.log('screenshots in ' + shotsDir);
    }
    check(b.problems.length === 0, 'no JavaScript errors in the whole run ' + JSON.stringify(b.problems));
  } finally {
    b.close();
  }
  console.log(failures.length ? `\n${failures.length} check(s) failed` : '\nsmoke test passed');
  process.exit(failures.length ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });

// FE-08: accessibility check (axe-core, WCAG 2.1 A and AA) of every screen, at rest and during an alert, plus
// screenshots for the README, the PDF and the jury. Needs the backend and the three dev servers (as for npm run e2e).
//
//   node e2e/a11y.mjs                    # report violations, exit 1 if there are any
//   node e2e/a11y.mjs --shots ../docs/screenshots
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { launchChrome, sleep } from './cdp.mjs';

const args = process.argv.slice(2);
const shotsDir = args.includes('--shots') ? resolve(args[args.indexOf('--shots') + 1]) : null;
const require = createRequire(import.meta.url);
const axeSource = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const API = 'http://localhost:4203';
const api = (path, body, method = 'POST') =>
  fetch(`${API}${path}`, body === undefined ? {} : { method, headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

if (shotsDir) mkdirSync(shotsDir, { recursive: true });
let violations = 0;

async function audit(page, name) {
  await page.evaluate(axeSource);
  const result = await page.evaluate(`axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } })
    .then(r => r.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 3).map(n => n.target.join(' ')) })))`);
  violations += result.length;
  console.log(`${result.length ? 'FAIL' : 'ok  '}  ${name}${result.length ? '' : ': no WCAG 2.1 AA violations'}`);
  for (const v of result) console.log(`        ${v.impact} ${v.id}: ${v.help}  [${v.nodes.join(' | ')}]`);
}

async function shot(page, name) {
  if (!shotsDir) return;
  const data = await page.screenshot();
  writeFileSync(resolve(shotsDir, `${name}.png`), Buffer.from(data, 'base64'));
}

const chrome = await launchChrome();
try {
  await api('/api/demo/stop', {});
  await api('/api/demo/phone-call', { active: false }, 'PUT');
  const senior = await chrome.open('http://localhost:4201/senior', { width: 390, height: 844 });
  const listen = await chrome.open('http://localhost:4202/listen', { width: 800, height: 1280 });
  const family = await chrome.open('http://localhost:4203/family', { width: 390, height: 844 });
  const setup = await chrome.open('http://localhost:4203/setup', { width: 390, height: 844 });
  const auditPage = await chrome.open('http://localhost:4203/audit', { width: 1280, height: 900 });
  await sleep(4000);

  await audit(senior, 'senior: resting');
  await shot(senior, 'senior-spokoj');
  await audit(listen, 'listen: waiting');
  await shot(listen, 'nasluch-czeka');
  await audit(family, 'family: panel');
  await audit(setup, 'setup: wizard');
  await shot(setup, 'ustawienia');

  // The simulated phone call: badge on the senior screen.
  await api('/api/demo/phone-call', { active: true }, 'PUT');
  await senior.waitFor(`document.body.innerText.includes('Trwa połączenie telefoniczne')`, 5000);
  await shot(senior, 'senior-polaczenie');
  await api('/api/demo/phone-call', { active: false }, 'PUT');

  // An alert: the classic fake police scenario, fast.
  await api('/api/demo/replay', { scenarioId: '01-fake-police-classic', mode: 'SCRIPTED', speed: 4 });
  await senior.waitFor(`!!document.querySelector('app-alert-view')`, 60000);
  await family.waitFor(`document.querySelectorAll('app-alert-card').length > 0`, 10000);
  await sleep(1500);
  await audit(senior, 'senior: alert');
  await shot(senior, 'senior-alert');
  await audit(listen, 'listen: alarm');
  await shot(listen, 'nasluch-alarm');
  await audit(family, 'family: alert card');
  await shot(family, 'rodzina-alert');

  await api('/api/demo/stop', {});
  await sleep(1500);
  await auditPage.evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('Odśwież'))?.click()`);
  await sleep(1500);
  await auditPage.evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('Szczegóły'))?.click()`);
  await sleep(1500);
  await audit(auditPage, 'audit: calls and AI calls');
  await shot(auditPage, 'audyt');
} finally {
  chrome.close();
}
console.log(violations ? `\n${violations} violation group(s)` : '\nNo accessibility violations');
process.exit(violations ? 1 : 0);

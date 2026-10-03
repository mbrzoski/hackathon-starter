// UC-01 end to end (docs/use-cases.md): the three apps open at once like three devices, a SCRIPTED call is played,
// the alert reaches senior, listen and family, and the decisions travel back. Needs the backend and the apps running.
//
//   npm run e2e         dev servers: senior :4201, listen :4202, family :4203 (npm start + make run-backend)
//   npm run e2e:demo    demo server https://localhost (deploy/run-demo.sh); TLS checked with deploy/ca.crt
//   node e2e/uc-01.mjs --base https://192.168.1.50
//
// Exit code 1 when a check fails. Every /ws/events message is also validated against contracts/openapi.yaml.
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { launchChrome, sleep } from './cdp.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const base = args.includes('--base') ? args[args.indexOf('--base') + 1].replace(/\/$/, '') : null;

// Demo with Caddy's own CA: Node has to trust deploy/ca.crt too, which only works from the start: run again with it.
const caFile = resolve(here, '../../deploy/ca.crt');
if (base?.startsWith('https:') && existsSync(caFile) && !process.env.NODE_EXTRA_CA_CERTS) {
  const run = spawnSync(process.execPath, process.argv.slice(1), { stdio: 'inherit', env: { ...process.env, NODE_EXTRA_CA_CERTS: caFile } });
  process.exit(run.status ?? 1);
}

const apps = base
  ? { senior: `${base}/senior`, listen: `${base}/listen`, family: `${base}/family`, api: base }
  : { senior: 'http://localhost:4201/senior', listen: 'http://localhost:4202/listen', family: 'http://localhost:4203/family', api: 'http://localhost:4203' };

let failures = 0;
const check = (ok, label, detail = '') => {
  if (!ok) failures++;
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${label}${detail ? `  (${detail})` : ''}`);
};
const api = (path, body) =>
  fetch(`${apps.api}${path}`, body === undefined ? {} : { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

// Contract validator for the events seen by this script (the apps use the compiled one).
const require = createRequire(import.meta.url);
const Ajv2020 = require('ajv/dist/2020').default;
const addFormats = require('ajv-formats').default;
const contract = require('yaml').parse(readFileSync(resolve(here, '../../contracts/openapi.yaml'), 'utf8'));
const ajv = new Ajv2020({ allErrors: true });
addFormats(ajv);
ajv.addKeyword('components');
ajv.addSchema({ $id: 'c', components: { schemas: contract.components.schemas } });
const validate = ajv.getSchema('c#/components/schemas/EventEnvelope');

const events = [];
let invalid = 0;
const familyOrigin = new URL(apps.family).origin;
const socket = new WebSocket(`${familyOrigin.replace(/^http/, 'ws')}/ws/events?role=family`, { headers: { Origin: familyOrigin } });
socket.onmessage = (m) => {
  const event = JSON.parse(m.data);
  if (!validate(event)) invalid++;
  events.push(event);
};
await new Promise((r, reject) => {
  socket.onopen = r;
  socket.onerror = () => reject(new Error(`Cannot open /ws/events at ${familyOrigin}: is everything running?`));
});

async function play(scenarioId, speed) {
  await api('/api/demo/stop', {});
  const from = events.length;
  const res = await api('/api/demo/replay', { scenarioId, mode: 'SCRIPTED', speed });
  check(res.status === 202, `replay ${scenarioId}`, `HTTP ${res.status}`);
  return {
    from,
    callId: async () => {
      for (let i = 0; i < 100; i++) {
        const started = events.slice(from).find((e) => e.type === 'call.started');
        if (started) return started.payload.callId;
        await sleep(100);
      }
      return null;
    },
    ended: async (timeoutMs = 120_000) => {
      const end = Date.now() + timeoutMs;
      while (Date.now() < end && !events.slice(from).some((e) => e.type === 'call.ended')) await sleep(200);
      return events.slice(from).find((e) => e.type === 'call.ended')?.payload ?? null;
    },
  };
}

const chrome = await launchChrome({ ignoreCertificateErrors: base?.startsWith('https:') ?? false });
try {
  console.log(`UC-01 against ${base ?? 'the dev servers (4201, 4202, 4203)'}\n`);
  const senior = await chrome.open(apps.senior, { width: 390, height: 844 });
  const listen = await chrome.open(apps.listen, { width: 800, height: 1280 });
  const family = await chrome.open(apps.family, { width: 390, height: 844 });
  const pages = { senior, listen, family };

  // 1. All three apps load and connect (the mode badge shows a real mode once an event arrived).
  for (const [name, page] of Object.entries(pages)) {
    const connected = await page.waitFor(`(() => { const t = document.querySelector('app-mode-badge')?.innerText ?? '';
      return /LIVE|REPLAY|SCRIPTED|MOCK/.test(t); })()`, 15_000);
    check(connected, `${name}: loads and connects to /ws/events`);
  }
  check(await senior.click('Włącz ochronę'), 'senior: "Włącz ochronę"');
  check(await listen.click('Włącz ochronę'), 'listen: "Włącz ochronę"');

  // 2. A normal call (04, grandson borrows money): no alert anywhere.
  const normal = await play('04-real-grandson', 10);
  const normalEnd = await normal.ended();
  check(normalEnd?.hadAlert === false, '04: call ends without an alert');
  check(!(await senior.evaluate(`!!document.querySelector('app-alert-view')`)), '04: senior shows no alert');

  // 3. The fake police call (01): the alert reaches all three devices at the same time.
  const cardsBefore = await family.evaluate(`document.querySelectorAll('app-alert-card').length`);
  const scam = await play('01-fake-police-classic', 3);
  const t0 = Date.now();
  const seen = {};
  const watch = {
    senior: `!!document.querySelector('app-alert-view')`,
    listen: `!!document.querySelector('.screen.alarm')`,
    family: `document.querySelectorAll('app-alert-card').length > ${cardsBefore}`,
  };
  while (Date.now() - t0 < 60_000 && Object.keys(seen).length < 3) {
    for (const [name, expression] of Object.entries(watch)) {
      if (!seen[name] && (await pages[name].evaluate(expression))) seen[name] = Date.now() - t0;
    }
    await sleep(100);
  }
  for (const name of Object.keys(watch)) check(seen[name] !== undefined, `01: alert on ${name}`, seen[name] !== undefined ? `${seen[name]} ms` : 'not shown');
  const times = Object.values(seen);
  check(times.length === 3 && Math.max(...times) - Math.min(...times) < 1500, '01: same alert on the three devices within 1.5 s');

  // 4. The senior decides; the family sees it.
  check(await senior.click('Rozłączam się'), 'senior: "Rozłączam się"');
  check(await family.waitFor(`document.body.innerText.includes('wybrał(a): rozłączam się')`, 10_000), 'family sees the senior decision');

  // 5. The family decides (with one stage not counted); the backend stores it.
  const familyAlert = await family.evaluate(`(() => { const card = document.querySelector('app-alert-card');
    card.querySelector('app-stage-timeline input')?.click();
    [...card.querySelectorAll('button')].find(b => b.textContent.includes('Potwierdzam oszustwo'))?.click();
    return card.querySelector('h2')?.textContent; })()`);
  let stored = false;
  for (let i = 0; i < 30 && !stored; i++) {
    await sleep(300);
    const list = await (await api('/api/alerts?limit=10')).json();
    stored = list.some((x) => x.decisions.some((d) => d.actor === 'family' && d.decision === 'confirmed_scam' && Date.now() - Date.parse(d.at) < 60_000));
  }
  check(stored, 'family decision stored by the backend', familyAlert);

  // 6. End of the call.
  const scamEnd = await scam.ended();
  check(scamEnd?.hadAlert === true, '01: call ends with hadAlert');
  check(await senior.waitFor(`document.body.innerText.includes('Rozmowa zakończona')`, 5_000), 'senior: "Rozmowa zakończona"');
  check(await listen.waitFor(`document.body.innerText.includes('Czekam na rozmowę')`, 5_000), 'listen: back to waiting');

  // 7. Failure states (dev only: /api/dev/emit does not exist in prod).
  const emit = (component, state) =>
    api('/api/dev/emit', { type: 'system.status', mode: 'SCRIPTED', at: new Date().toISOString(), payload: { component, state, message: `e2e ${component} ${state}`, at: new Date().toISOString() } });
  if ((await emit('audio', 'down')).status === 202) {
    await sleep(800);
    check((await senior.text()).includes('Nie słyszę rozmowy'), 'audio down: senior "Nie słyszę rozmowy"');
    check((await family.evaluate(`document.querySelector('app-system-status-bar')?.innerText`))?.includes('Nie słyszę rozmowy'), 'audio down: family status bar');
    await emit('audio', 'ok');
    await sleep(800);
    check((await senior.text()).includes('Anioł Stróż słucha'), 'audio back: senior green again');
  } else {
    console.log('skip  failure states (no /api/dev/emit: prod profile)');
  }

  // 8. Contract and console.
  check(invalid === 0, 'every /ws/events message matches the contract', `${events.length} messages`);
  for (const [name, page] of Object.entries(pages)) {
    check(page.errors.length === 0, `${name}: no console errors or CSP violations`, page.errors.slice(0, 2).join(' | ').slice(0, 200));
  }
} finally {
  socket.close();
  chrome.close();
}

console.log(failures ? `\n${failures} check(s) failed` : '\nUC-01 passed');
process.exit(failures ? 1 : 0);

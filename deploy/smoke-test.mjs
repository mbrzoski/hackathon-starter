// Smoke test of the demo web server (WEB-02..WEB-06). Plain Node, no dependencies. Exit code 1 on any failure.
//
//   node deploy/smoke-test.mjs --host 192.168.1.50                 # variant (a), after deploy/run-demo.sh
//   node deploy/smoke-test.mjs --host localhost --https-port 8443 --http-port 8088
//   node deploy/smoke-test.mjs --tunnel https://abc.trycloudflare.com   # variant (b)
//
// Variant (a) downloads the CA from http://<host>/ca.crt and verifies TLS with it, the same way a tablet will.
import http from 'node:http';
import https from 'node:https';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, arg, i, all) => (arg.startsWith('--') ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs), []),
);
const site = resolve(dirname(fileURLToPath(import.meta.url)), 'site');
const tunnel = args.tunnel ? new URL(args.tunnel) : null;
const host = tunnel ? tunnel.hostname : (args.host ?? 'localhost');
const httpsPort = tunnel ? 443 : Number(args['https-port'] ?? 443);
const httpPort = Number(args['http-port'] ?? 80);
const origin = `https://${host}${httpsPort === 443 ? '' : `:${httpsPort}`}`;

let failures = 0;
const check = (ok, label, detail = '') => {
  if (!ok) failures++;
  console.log(`${ok ? 'ok  ' : 'FAIL'}  ${label}${detail ? `  (${detail})` : ''}`);
};

function request(client, options) {
  return new Promise((resolveRequest, reject) => {
    const req = client.request({ timeout: 10_000, ...options }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolveRequest({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('upgrade', (res, socket) => {
      socket.destroy();
      resolveRequest({ status: res.statusCode, headers: res.headers, body: Buffer.alloc(0) });
    });
    req.on('error', reject);
    req.end();
  });
}

let ca;
if (!tunnel) {
  const start = await request(http, { host, port: httpPort, path: '/' }).catch((e) => ({ status: e.message }));
  check(start.status === 200 && String(start.body).includes('Pobierz ca.crt'), 'http:// start page', `status ${start.status}`);
  const cert = await request(http, { host, port: httpPort, path: '/ca.crt' }).catch((e) => ({ status: e.message }));
  check(cert.status === 200 && String(cert.body).includes('BEGIN CERTIFICATE'), 'http:///ca.crt is the CA certificate');
  ca = cert.body;
  const redirect = await request(http, { host, port: httpPort, path: '/senior' }).catch((e) => ({ status: e.message }));
  check([301, 308].includes(redirect.status) && redirect.headers.location?.startsWith('https://'), 'http:// redirects to https://');
  for (const app of ['senior', 'listen', 'family']) {
    const go = await request(http, { host, port: httpPort, path: `/go/${app}` }).catch((e) => ({ status: e.message, headers: {} }));
    check(go.status === 302 && go.headers.location?.endsWith(`/${app}`), `start page button /go/${app}`, go.headers.location);
  }
}

const get = (path, headers = {}) =>
  request(https, { host, port: httpsPort, path, headers: { Host: new URL(origin).host, ...headers }, ca, servername: /^[\d.]+$/.test(host) ? undefined : host });

const mainOf = (file) => String(readFileSync(resolve(site, file))).match(/main-[\w-]+\.js/)?.[0];
const expected = { senior: mainOf('index-senior.html'), listen: mainOf('index-listen.html'), family: mainOf('index-family.html') };

const routes = [['/senior', 'senior'], ['/listen', 'listen'], ['/family', 'family'], ['/setup', 'family'], ['/audit', 'family'], ['/nie-ma', 'senior'], ['/', 'senior']];
for (const [path, app] of routes) {
  try {
    const res = await get(path);
    const main = String(res.body).match(/main-[\w-]+\.js/)?.[0];
    check(res.status === 200 && main === expected[app], `${path} serves the ${app} app (TLS verified${tunnel ? '' : ' with ca.crt'})`, `status ${res.status}`);
    if (path === '/senior') {
      const h = res.headers;
      const csp = h['content-security-policy'] ?? '';
      check(csp.includes("default-src 'self'") && csp.includes("script-src 'self'") && csp.includes(`wss://${new URL(origin).host}`), 'Content-Security-Policy', csp.slice(0, 60));
      check(h['permissions-policy'] === 'microphone=(self)', 'Permissions-Policy: microphone=(self)');
      check(h['referrer-policy'] === 'no-referrer', 'Referrer-Policy: no-referrer');
      check(h['cache-control'] === 'no-cache', 'index.html: Cache-Control no-cache', h['cache-control']);
    }
  } catch (e) {
    check(false, `${path}`, e.message);
  }
}

const hashed = readdirSync(site).find((f) => /^main-.*\.js$/.test(f));
const asset = await get(`/${hashed}`).catch((e) => ({ status: e.message, headers: {} }));
check(asset.status === 200 && /max-age=31536000/.test(asset.headers['cache-control'] ?? ''), 'hashed file cached for a year', asset.headers['cache-control']);

const status = await get('/api/status').catch((e) => ({ status: e.message, body: '' }));
let mode = '?';
try {
  mode = JSON.parse(String(status.body)).mode;
} catch {
  // reported below
}
check(status.status === 200 && mode !== '?', '/api/status through the proxy', `mode ${mode}`);

const ws = (wsOrigin) =>
  get('/ws/events?role=family', {
    Connection: 'Upgrade', Upgrade: 'websocket', 'Sec-WebSocket-Version': '13',
    'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ==', Origin: wsOrigin,
  }).catch((e) => ({ status: e.message }));
check((await ws(origin)).status === 101, `WebSocket /ws/events from ${origin}`);
check((await ws('https://evil.example')).status === 403, 'WebSocket from a foreign origin is rejected');

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);

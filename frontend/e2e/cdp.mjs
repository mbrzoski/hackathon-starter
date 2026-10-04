// Minimal Chrome DevTools Protocol driver for the e2e test: no Playwright download, it uses the installed Chrome.
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function findChrome() {
  const candidates = [
    process.env.CHROME,
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
  ];
  const found = candidates.find((c) => c && existsSync(c));
  if (!found) throw new Error('Chrome not found: set CHROME=/path/to/chrome');
  return found;
}

export async function launchChrome({ port = 9555, ignoreCertificateErrors = false } = {}) {
  const profile = mkdtempSync(join(tmpdir(), 'aniol-e2e-'));
  const chrome = spawn(findChrome(), [
    '--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, '--no-first-run',
    ...(ignoreCertificateErrors ? ['--ignore-certificate-errors'] : []), 'about:blank',
  ], { stdio: 'ignore' });
  for (let i = 0; i < 100; i++) {
    try {
      await fetch(`http://127.0.0.1:${port}/json/version`);
      break;
    } catch {
      await sleep(100);
    }
  }

  async function open(url, { width = 390, height = 844 } = {}) {
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
    const ws = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((r) => (ws.onopen = r));
    let id = 0;
    const pending = new Map();
    const errors = [];
    ws.onmessage = (m) => {
      const d = JSON.parse(m.data);
      if (pending.has(d.id)) {
        pending.get(d.id)(d.result ?? d.error);
        pending.delete(d.id);
      } else if (d.method === 'Runtime.exceptionThrown') {
        errors.push(d.params.exceptionDetails.exception?.description ?? d.params.exceptionDetails.text);
      } else if (d.method === 'Runtime.consoleAPICalled' && d.params.type === 'error') {
        errors.push(d.params.args.map((a) => a.value ?? a.description).join(' '));
      } else if (d.method === 'Log.entryAdded' && d.params.entry.level === 'error' && d.params.entry.source !== 'network') {
        errors.push(`${d.params.entry.source}: ${d.params.entry.text}`); // CSP violations land here
      }
    };
    const send = (method, params = {}) =>
      new Promise((r) => {
        pending.set(++id, r);
        ws.send(JSON.stringify({ id, method, params }));
      });
    await send('Runtime.enable');
    await send('Log.enable');
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 800 });
    // Each app stands for its own device, always in the foreground: stop Chrome from throttling background tabs.
    await send('Emulation.setFocusEmulationEnabled', { enabled: true });
    await send('Page.navigate', { url });
    const evaluate = async (expression) =>
      (await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })).result?.value;
    return {
      errors,
      evaluate,
      text: () => evaluate(`document.body.innerText.replace(/\\s+/g, ' ')`),
      click: (label) =>
        evaluate(`(() => { const el = [...document.querySelectorAll('button, a')].find(e => e.textContent.includes(${JSON.stringify(label)}));
          if (el) el.click(); return !!el; })()`),
      reload: () => send('Page.reload'),
      screenshot: async () => (await send('Page.captureScreenshot', { format: 'png' })).data,
      async waitFor(expression, timeoutMs = 20_000) {
        const end = Date.now() + timeoutMs;
        while (Date.now() < end) {
          if (await evaluate(expression)) return true;
          await sleep(150);
        }
        return false;
      },
    };
  }

  return {
    open,
    /** Kills Chrome and deletes its temporary profile right away (callers exit the process just after). */
    close() {
      chrome.kill('SIGKILL');
      try {
        rmSync(profile, { recursive: true, force: true, maxRetries: 20, retryDelay: 100 });
      } catch {
        // A file still held by the dying browser: the OS temp cleaner removes the rest.
      }
    },
  };
}

// Serves the three apps (senior :4201, listen :4202, family :4203) side by side, on Windows and macOS alike (FF-06).
// When one dev server stops, the others are stopped too, and the exit code is passed on.
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
// ng.js through this node rather than `ng`: Windows cannot spawn ng(.cmd) without a shell.
const ng = resolve(root, 'node_modules/@angular/cli/bin/ng.js');
const apps = ['senior', 'listen', 'family'];

const children = apps.map((app) =>
  spawn(process.execPath, [ng, 'serve', '-c', app], { cwd: root, stdio: 'inherit' }),
);

let exiting = false;
for (const child of children) {
  child.on('exit', (code) => {
    if (exiting) {
      return;
    }
    exiting = true;
    for (const other of children) {
      if (other !== child && other.exitCode === null) {
        other.kill();
      }
    }
    process.exitCode = code ?? 1;
  });
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    exiting = true;
    children.forEach((child) => child.kill());
  });
}

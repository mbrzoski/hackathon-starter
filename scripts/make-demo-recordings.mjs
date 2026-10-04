#!/usr/bin/env node
// Makes the REPLAY recordings (EV-04 stand-in) from the written scenarios with the macOS Polish voice "Zosia":
// backend/recordings/<scenarioId>.wav, PCM 16 kHz, mono, 16-bit, the format of /ws/audio and Vosk.
// These are SYNTHETIC speech, not recordings of people. Replace them with the team's own recordings when you have
// them (same file name and format). Needs macOS (say, afconvert).
//
//   node scripts/make-demo-recordings.mjs              # every scenario
//   node scripts/make-demo-recordings.mjs 01 04        # scenarios whose id starts with 01 or 04
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scenarioDir = join(root, 'backend/src/main/resources/scenarios');
const outDir = join(root, 'backend/recordings');
const filters = process.argv.slice(2);
const RATE = 16000;
const MAX_PAUSE_MS = 1500; // the written delays include reading time; a real pause between turns is shorter

if (process.platform !== 'darwin') {
  console.error('This script needs macOS (say, afconvert). Record the scenarios yourself: 16 kHz mono 16-bit WAV.');
  process.exit(1);
}
const voices = execFileSync('say', ['-v', '?'], { encoding: 'utf8' });
if (!/^Zosia\s/m.test(voices)) {
  console.error('The Polish voice "Zosia" is missing: System Settings > Accessibility > Spoken Content > System voice > Manage voices.');
  process.exit(1);
}

function pcmOf(wavFile) {
  const wav = readFileSync(wavFile);
  let off = 12;
  while (off < wav.length) {
    const id = wav.toString('ascii', off, off + 4);
    const size = wav.readUInt32LE(off + 4);
    if (id === 'data') return wav.subarray(off + 8, off + 8 + size);
    off += 8 + size + (size % 2);
  }
  throw new Error(`no data chunk in ${wavFile}`);
}

function wavOf(pcm) {
  const header = Buffer.alloc(44);
  header.write('RIFF', 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write('WAVE', 8);
  header.write('fmt ', 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(1, 22); // mono
  header.writeUInt32LE(RATE, 24);
  header.writeUInt32LE(RATE * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36);
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

mkdirSync(outDir, { recursive: true });
const work = mkdtempSync(join(tmpdir(), 'aniol-rec-'));
try {
  const files = readdirSync(scenarioDir).filter((f) => f.endsWith('.json')).sort();
  for (const file of files) {
    const scenario = JSON.parse(readFileSync(join(scenarioDir, file), 'utf8'));
    if (filters.length && !filters.some((f) => scenario.scenarioId.startsWith(f))) continue;
    const parts = [];
    scenario.segments.forEach((segment, i) => {
      const pause = Math.min(segment.delayMs ?? 0, MAX_PAUSE_MS);
      parts.push(Buffer.alloc(Math.round((pause / 1000) * RATE) * 2));
      const aiff = join(work, `${i}.aiff`);
      const wav = join(work, `${i}.wav`);
      // The caller (B) speaks a little faster than the senior (A), so the two turns sound different.
      const rate = segment.speaker === 'B' ? '185' : '160';
      execFileSync('say', ['-v', 'Zosia', '-r', rate, '-o', aiff, segment.text]);
      execFileSync('afconvert', ['-f', 'WAVE', '-d', `LEI16@${RATE}`, '-c', '1', aiff, wav]);
      parts.push(pcmOf(wav));
    });
    parts.push(Buffer.alloc(RATE * 2)); // one second of silence at the end
    const out = join(outDir, `${scenario.scenarioId}.wav`);
    const pcm = Buffer.concat(parts);
    writeFileSync(out, wavOf(pcm));
    console.log(`${scenario.scenarioId}.wav  ${(pcm.length / (RATE * 2)).toFixed(1)} s`);
  }
} finally {
  rmSync(work, { recursive: true, force: true });
}
if (!existsSync(outDir)) process.exit(1);

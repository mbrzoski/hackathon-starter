import { DestroyRef, Signal, inject, signal } from '@angular/core';

/** 65 -> "01:05" */
export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/** "2026-10-04T12:46:00Z" -> local "12:46" */
export function formatClock(iso: string): string {
  return new Date(iso).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' });
}

/** Current time in ms, ticking every second while the injecting component lives. */
export function injectNow(): Signal<number> {
  const now = signal(Date.now());
  const timer = setInterval(() => now.set(Date.now()), 1000);
  inject(DestroyRef).onDestroy(() => clearInterval(timer));
  return now.asReadonly();
}

import { DOCUMENT } from '@angular/common';
import { Injectable, InjectionToken, inject } from '@angular/core';

/** Creates the AudioContext for the alert sound; null where there is none. Replaced in tests. */
export const AUDIO_CONTEXT_FACTORY = new InjectionToken<() => AudioContext | null>('AUDIO_CONTEXT_FACTORY', {
  providedIn: 'root',
  factory: () => () => (typeof AudioContext !== 'undefined' ? new AudioContext() : null),
});

/**
 * Plays a short alarm tone for a new high alert. Browsers only allow sound after the person has interacted with the
 * page, so the sound is unlocked on the first tap or key press and silent before that.
 */
@Injectable({ providedIn: 'root' })
export class AlertNotifier {
  private readonly createContext = inject(AUDIO_CONTEXT_FACTORY);
  private context: AudioContext | null = null;

  constructor() {
    const doc = inject(DOCUMENT);
    const unlock = () => {
      this.unlock();
      doc.removeEventListener('pointerdown', unlock);
      doc.removeEventListener('keydown', unlock);
    };
    doc.addEventListener('pointerdown', unlock);
    doc.addEventListener('keydown', unlock);
  }

  get unlocked(): boolean {
    return this.context !== null;
  }

  unlock(): void {
    try {
      this.context ??= this.createContext();
      void this.context?.resume();
    } catch {
      this.context = null;
    }
  }

  /** Three short beeps; does nothing before the first interaction. */
  beep(): void {
    const ctx = this.context;
    if (!ctx) {
      return;
    }
    try {
      for (let i = 0; i < 3; i++) {
        const at = ctx.currentTime + i * 0.35;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.value = 880;
        gain.gain.setValueAtTime(0.25, at);
        gain.gain.exponentialRampToValueAtTime(0.001, at + 0.25);
        osc.connect(gain).connect(ctx.destination);
        osc.start(at);
        osc.stop(at + 0.25);
      }
    } catch {
      // No sound is not an error: the card and the title still show the alert.
    }
  }
}

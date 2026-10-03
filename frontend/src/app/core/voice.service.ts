import { Injectable, InjectionToken, inject, signal } from '@angular/core';

/** The browser's speech synthesis, or null where there is none; replaced in tests. */
export const SPEECH_SYNTHESIS = new InjectionToken<SpeechSynthesis | null>('SPEECH_SYNTHESIS', {
  providedIn: 'root',
  factory: () =>
    typeof speechSynthesis !== 'undefined' && typeof SpeechSynthesisUtterance !== 'undefined' ? speechSynthesis : null,
});

/** Reads texts aloud with the first pl-PL voice (WEB-09). Never throws: missing speech is shown, not an error. */
@Injectable({ providedIn: 'root' })
export class VoiceService {
  private readonly synth = inject(SPEECH_SYNTHESIS);
  private readonly voice = signal<SpeechSynthesisVoice | null>(null);

  /** False when the device cannot speak at all, or has no Polish voice. */
  readonly available = signal(false);

  constructor() {
    if (!this.synth) {
      return;
    }
    this.pickVoice();
    // Voices often load asynchronously.
    this.synth.addEventListener?.('voiceschanged', () => this.pickVoice());
  }

  /** Must run from a user gesture (WEB-07): browsers block speech until then. */
  unlock(): void {
    try {
      this.synth?.speak(this.utterance(''));
    } catch {
      // Speech stays unavailable; the text is on screen.
    }
  }

  /** Returns false when nothing was spoken. */
  speak(text: string): boolean {
    const voice = this.voice();
    if (!this.synth || !voice) {
      return false;
    }
    try {
      this.synth.cancel();
      const utterance = this.utterance(text);
      utterance.voice = voice;
      this.synth.speak(utterance);
      return true;
    } catch {
      return false;
    }
  }

  private pickVoice(): void {
    const voices = this.synth?.getVoices() ?? [];
    const polish = voices.find((v) => v.lang.toLowerCase().replace('_', '-').startsWith('pl')) ?? null;
    this.voice.set(polish);
    this.available.set(!!polish);
  }

  private utterance(text: string): SpeechSynthesisUtterance {
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'pl-PL';
    return utterance;
  }
}

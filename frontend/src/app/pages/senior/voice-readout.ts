import { Component, effect, inject, input, untracked } from '@angular/core';
import { VoiceService } from '../../core/voice.service';

/** Reads the alert text aloud once per new alert (WEB-09). Without speech it says so. */
@Component({
  selector: 'app-voice-readout',
  template: `
    @if (!voice.available()) {
      <p class="info" role="note">Brak głosu po polsku. Przeczytaj tekst na ekranie.</p>
    }
  `,
  styles: `
    .info { margin: 0; font-size: 28px; }
  `,
})
export class VoiceReadout {
  protected readonly voice = inject(VoiceService);
  readonly text = input.required<string>();
  /** Changes for every new alert; the text is read once per key. */
  readonly key = input.required<string>();

  constructor() {
    effect(() => {
      this.key();
      const text = untracked(this.text);
      untracked(() => this.voice.speak(text));
    });
  }
}

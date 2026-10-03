import { Component, effect, inject, input, untracked } from '@angular/core';
import { VoiceService } from '../../core/voice.service';
import { Icon } from '../../shared/icon';

/** Reads the alert text aloud once per new alert, with "Powtórz" (WEB-09). Without speech it says so. */
@Component({
  selector: 'app-voice-readout',
  imports: [Icon],
  template: `
    @if (voice.available()) {
      <button type="button" class="repeat" (click)="voice.speak(text())">
        <app-icon name="play" [size]="28" /> Powtórz
      </button>
    } @else {
      <p class="info" role="note">Brak głosu po polsku. Przeczytaj tekst na ekranie.</p>
    }
  `,
  styles: `
    .repeat { font: inherit; font-size: 28px; font-weight: 700; min-height: 64px; padding: 0 24px; border-radius: 14px;
      border: 3px solid #fff; background: transparent; color: #fff; display: inline-flex; align-items: center; gap: 10px;
      cursor: pointer; }
    .repeat:focus-visible { outline: 4px solid var(--highlight); outline-offset: 2px; }
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

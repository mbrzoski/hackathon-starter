import { Component, computed, input } from '@angular/core';
import { environment } from '../../environments/environment';
import { Mode } from '../core/contracts';

const MODE_TEXT: Record<Mode, string> = {
  LIVE: 'LIVE',
  REPLAY: 'REPLAY: nagranie, prawdziwe AI',
  SCRIPTED: 'SCRIPTED: tekst pisany, prawdziwe AI',
  MOCK: 'MOCK: dane demo, bez AI',
};

/** Shown on every front (FE-05). */
@Component({
  selector: 'app-mode-badge',
  template: `<span class="badge" [class.dark]="dark()" [attr.title]="text()">{{ text() }}</span>`,
  styles: `
    .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;
      background: var(--highlight); color: #4a3500; border: 1px solid #c9a227; }
    .badge.dark { background: transparent; color: var(--highlight); border-color: var(--highlight); }
  `,
})
export class ModeBadgeComponent {
  // TODO: the mode comes from the backend (system.status) once it exists; until then it follows the environment.
  readonly mode = input<Mode>(environment.useMocks ? 'MOCK' : 'LIVE');
  readonly dark = input(false);
  protected readonly text = computed(() => MODE_TEXT[this.mode()]);
}

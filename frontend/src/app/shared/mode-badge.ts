import { Component, computed, inject, input } from '@angular/core';
import { Mode } from '../api/model/models';
import { EventsService } from '../core/events.service';

export const MODE_TEXT: Record<Mode, string> = {
  LIVE: 'LIVE',
  REPLAY: 'REPLAY: nagranie, prawdziwe AI',
  SCRIPTED: 'SCRIPTED: tekst rozmowy, prawdziwe AI',
  MOCK: 'MOCK: bez AI, dane demonstracyjne',
};

/** Always visible (FE-05). The mode comes from the last event on /ws/events. */
@Component({
  selector: 'app-mode-badge',
  template: `<span class="badge" [class.dark]="dark()" [class.large]="large()" [class.unknown]="!events.mode()"
    [attr.aria-label]="text()" [attr.title]="text()">{{ compact() ? shortText() : text() }}</span>`,
  styles: `
    .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;
      background: var(--highlight); color: #4a3500; border: 1px solid #c9a227; }
    .badge.dark { background: transparent; color: var(--highlight); border-color: var(--highlight); }
    .badge.large { font-size: 28px; padding: 4px 16px; }
    .badge.unknown { background: var(--surface); color: var(--muted); border-color: var(--border); }
  `,
})
export class ModeBadge {
  protected readonly events = inject(EventsService);
  readonly dark = input(false);
  /** Senior screen: min. 28 px text (WEB-11). */
  readonly large = input(false);
  /** Mode name only (the full text stays in aria-label and title): for the senior screen's single-line header. */
  readonly compact = input(false);
  protected readonly shortText = computed(() => this.events.mode() ?? 'TRYB ?');
  protected readonly text = computed(() => {
    const mode = this.events.mode();
    return mode ? MODE_TEXT[mode] : 'Tryb nieznany';
  });
}

import { Component, computed, input, output } from '@angular/core';
import { SeniorStatus } from '../../core/senior-status';
import { Icon, IconName } from '../../shared/icon';

type Tone = 'green' | 'yellow' | 'red';

export const STATUS_VIEW: Record<SeniorStatus, { tone: Tone; icon: IconName; text: string }> = {
  protected: { tone: 'green', icon: 'shield', text: 'Anioł Stróż słucha. Nic nie jest nagrywane.' },
  basic: { tone: 'yellow', icon: 'warning', text: 'Podstawowa ochrona (bez AI)' },
  paused: { tone: 'yellow', icon: 'pause', text: 'Ochrona wstrzymana' },
  not_hearing: { tone: 'red', icon: 'mic-off', text: 'Nie słyszę rozmowy' },
  offline: { tone: 'red', icon: 'cloud-off', text: 'Anioł Stróż jest offline' },
};

/** One large sentence and icon for the resting state; the state is never colour only (WEB-11). */
@Component({
  selector: 'app-status-panel',
  imports: [Icon],
  template: `
    <section class="panel" [class]="view().tone" role="status" aria-live="polite">
      <span class="icon"><app-icon [name]="view().icon" [size]="96" /></span>
      <p class="text">{{ view().text }}</p>
    </section>
    @if (canPause()) {
      <button type="button" class="btn-secondary decision" (click)="togglePause.emit()">
        <app-icon [name]="paused() ? 'play' : 'pause'" [size]="32" />
        {{ paused() ? 'Wznów ochronę' : 'Wstrzymaj dla tej rozmowy' }}
      </button>
    }
  `,
  styleUrl: './status-panel.scss',
})
export class StatusPanel {
  readonly status = input.required<SeniorStatus>();
  /** The pause button only makes sense during a call. */
  readonly canPause = input(false);
  readonly paused = input(false);
  readonly togglePause = output<void>();

  protected readonly view = computed(() => STATUS_VIEW[this.status()]);
}

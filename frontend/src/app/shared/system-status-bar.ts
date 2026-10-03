import { Component, computed, inject } from '@angular/core';
import { EventsService } from '../core/events.service';
import { selectSeniorStatus } from '../core/senior-status';
import { Icon } from './icon';
import { STATUS_VIEW } from './status-view';

/** Compact protection state, the same states as on the senior screen (selectSeniorStatus). */
@Component({
  selector: 'app-system-status-bar',
  imports: [Icon],
  template: `
    <div class="bar" [class]="view().tone" role="status" aria-live="polite">
      <app-icon [name]="view().icon" [size]="22" />
      <span>{{ view().text }}</span>
    </div>
  `,
  styles: `
    .bar { display: flex; align-items: center; gap: 10px; padding: 8px 16px; font-weight: 700; }
    .green { background: var(--safe-deep); color: #fff; }
    .yellow { background: var(--highlight); color: var(--text); }
    .red { background: var(--alarm-deep); color: #fff; }
  `,
})
export class SystemStatusBar {
  private readonly events = inject(EventsService);
  protected readonly view = computed(
    () => STATUS_VIEW[selectSeniorStatus(this.events.connection(), this.events.systemStatus(), this.events.activeCall())],
  );
}

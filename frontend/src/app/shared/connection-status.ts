import { Component, inject } from '@angular/core';
import { EventsService } from '../core/events.service';
import { Icon } from './icon';

/** Red bar while /ws/events is not connected: a failure is never silent (FE-06). */
@Component({
  selector: 'app-connection-status',
  imports: [Icon],
  template: `
    @if (!events.online()) {
      <div class="offline" role="alert">
        <app-icon name="warning" [size]="24" />
        <span>Anioł Stróż jest offline</span>
        @if (events.connection() === 'connecting') {
          <span class="hint">Łączę ponownie…</span>
        }
      </div>
    }
  `,
  styles: `
    .offline { display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: var(--alarm-deep);
      color: #fff; font-weight: 700; font-size: 18px; }
    .hint { font-weight: 400; font-size: 15px; opacity: 0.9; }
  `,
})
export class ConnectionStatus {
  protected readonly events = inject(EventsService);
}

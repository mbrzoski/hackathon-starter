import { Component, input } from '@angular/core';

export type ConnectionTone = 'ok' | 'warn' | 'error';

/** Dot plus text: the state is never colour only. */
@Component({
  selector: 'app-connection-status',
  template: `<span class="dot" [class]="tone()"></span><span>{{ label() }}</span>`,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 8px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; flex: none; }
    .ok { background: var(--safe); }
    .warn { background: var(--warn); }
    .error { background: var(--alarm); }
  `,
})
export class ConnectionStatusComponent {
  readonly tone = input<ConnectionTone>('ok');
  readonly label = input.required<string>();
}

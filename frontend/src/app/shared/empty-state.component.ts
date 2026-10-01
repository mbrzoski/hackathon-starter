import { Component, input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  template: `<div class="state empty">{{ message() }}</div>`,
})
export class EmptyStateComponent {
  readonly message = input('Nothing here yet.');
}

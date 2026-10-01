import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-error-banner',
  template: `
    <div class="state error" role="alert">
      <span>{{ message() }}</span>
      @if (retryable()) {
        <button type="button" (click)="retry.emit()">Retry</button>
      }
    </div>
  `,
})
export class ErrorBannerComponent {
  readonly message = input.required<string>();
  readonly retryable = input(false);
  readonly retry = output<void>();
}

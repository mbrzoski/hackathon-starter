import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  template: `
    <section class="card">
      @if (title()) {
        <h2>{{ title() }}</h2>
      }
      <ng-content />
    </section>
  `,
})
export class CardComponent {
  readonly title = input('');
}

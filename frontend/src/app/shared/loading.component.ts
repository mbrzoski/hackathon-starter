import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loading',
  template: `<div class="state loading" role="status"><span class="spinner"></span> {{ label() }}</div>`,
})
export class LoadingComponent {
  readonly label = input('Loading…');
}

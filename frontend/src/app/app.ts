import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConnectionStatus } from './shared/connection-status';
import { APP_CONNECTION_BAR } from './target/app-target';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConnectionStatus],
  template: `
    @if (connectionBar) {
      <app-connection-status />
    }
    <router-outlet />
  `,
})
export class App {
  protected readonly connectionBar = APP_CONNECTION_BAR;
}

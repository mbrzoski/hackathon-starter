import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConnectionStatus } from './shared/connection-status';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConnectionStatus],
  template: `<app-connection-status /><router-outlet />`,
})
export class App {}

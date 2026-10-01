import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <strong>Hackathon Starter</strong>
      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Dashboard</a>
        <a routerLink="/playground" routerLinkActive="active">Playground</a>
      </nav>
      @if (mocks) {
        <span class="badge">MOCK DATA</span>
      }
    </header>
    <main><router-outlet /></main>
  `,
})
export class AppComponent {
  protected readonly mocks = environment.useMocks;
}

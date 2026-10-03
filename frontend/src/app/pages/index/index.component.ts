import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';
import { ModeBadgeComponent } from '../../shared/mode-badge.component';

@Component({
  selector: 'app-index',
  imports: [RouterLink, IconComponent, ModeBadgeComponent],
  template: `
    <main class="wrap">
      <header>
        <h1><app-icon name="shield" [size]="32" /> Anioł Stróż</h1>
        <app-mode-badge />
      </header>
      <p>Trzy osobne ekrany jednej aplikacji:</p>
      <ul>
        <li>
          <a routerLink="/listen"><strong>Nasłuch</strong></a>
          <span>tablet lub laptop przy telefonie stacjonarnym</span>
        </li>
        <li>
          <a routerLink="/senior"><strong>Senior</strong></a>
          <span>aplikacja seniora</span>
        </li>
        <li>
          <a routerLink="/family"><strong>Panel rodziny</strong></a>
          <span>podgląd dla bliskich</span>
        </li>
      </ul>
    </main>
  `,
  styles: `
    .wrap { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
    header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
    h1 { display: flex; align-items: center; gap: 10px; color: var(--navy); }
    ul { list-style: none; padding: 0; display: grid; gap: 12px; }
    li { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 16px; display: grid; gap: 4px; }
    li a { font-size: 20px; }
    li span { color: var(--muted); }
  `,
})
export class IndexComponent {}

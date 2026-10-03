import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../shared/empty-state.component';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, EmptyStateComponent],
  template: `
    <div class="wrap">
      <h1>Nie znaleziono strony</h1>
      <app-empty-state message="Taka strona nie istnieje." />
      <p><a routerLink="/">Wróć do listy ekranów</a></p>
    </div>
  `,
  styles: `.wrap { max-width: 720px; margin: 0 auto; padding: 24px 16px; }`,
})
export class NotFoundComponent {}

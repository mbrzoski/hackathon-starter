import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../shared/empty-state.component';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, EmptyStateComponent],
  template: `
    <h1>Page not found</h1>
    <app-empty-state message="This page does not exist." />
    <p><a routerLink="/">Back to the dashboard</a></p>
  `,
})
export class NotFoundComponent {}

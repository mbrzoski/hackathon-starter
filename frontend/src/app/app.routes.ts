import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/home/home.component').then((m) => m.HomeComponent) },
  {
    path: 'playground',
    loadComponent: () => import('./pages/playground/playground.component').then((m) => m.PlaygroundComponent),
  },
  { path: '**', loadComponent: () => import('./pages/not-found/not-found.component').then((m) => m.NotFoundComponent) },
];

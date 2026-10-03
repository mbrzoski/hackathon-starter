import { Routes } from '@angular/router';

// FE-01: three separate fronts, one Angular app.
export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/index/index.component').then((m) => m.IndexComponent) },
  { path: 'listen', loadComponent: () => import('./pages/listen/listen.component').then((m) => m.ListenComponent) },
  { path: 'senior', loadComponent: () => import('./pages/senior/senior.component').then((m) => m.SeniorComponent) },
  { path: 'family', loadComponent: () => import('./pages/family/family.component').then((m) => m.FamilyComponent) },
  { path: '**', loadComponent: () => import('./pages/not-found/not-found.component').then((m) => m.NotFoundComponent) },
];

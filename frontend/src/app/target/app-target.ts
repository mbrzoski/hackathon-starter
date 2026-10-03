import { Routes } from '@angular/router';

// Which of the three apps this build is. Replaced per build configuration in angular.json (fileReplacements):
// app-target.listen.ts, app-target.family.ts. Same code base for all of them (FE-01), only the routes differ.
export const APP_HOME = 'senior';

export const APP_ROUTES: Routes = [
  { path: 'senior', title: 'Anioł Stróż', loadComponent: () => import('../pages/senior/senior').then((m) => m.Senior) },
];

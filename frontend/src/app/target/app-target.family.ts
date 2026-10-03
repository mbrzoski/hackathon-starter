import { Routes } from '@angular/router';

// Family app in a browser: panel, setup and audit (family, jury, team). See app-target.ts.
export const APP_HOME = 'family';

export const APP_ROUTES: Routes = [
  { path: 'family', title: 'Anioł Stróż · Panel rodziny', loadComponent: () => import('../pages/family/family').then((m) => m.Family) },
  { path: 'setup', title: 'Anioł Stróż · Ustawienia', loadComponent: () => import('../pages/setup/setup').then((m) => m.Setup) },
  { path: 'audit', title: 'Anioł Stróż · Audyt', loadComponent: () => import('../pages/audit/audit').then((m) => m.Audit) },
];

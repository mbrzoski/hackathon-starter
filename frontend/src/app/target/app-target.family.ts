import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';

// Family app in a browser: panel, setup and audit (family, jury, team). See app-target.ts.
export const APP_HOME = 'family';

/** The family panel shows offline in its own status bar (SystemStatusBar). */
export const APP_CONNECTION_BAR = false;

/**
 * The family app always opens on the panel: settings are reached from it, never as the first page (a bookmarked or
 * reloaded /setup goes to /family too).
 */
export const notOnStart = () => {
  const router = inject(Router);
  return router.navigated || router.createUrlTree(['/family']);
};

export const APP_ROUTES: Routes = [
  { path: 'family', title: 'Anioł Stróż · Panel rodziny', loadComponent: () => import('../pages/family/family').then((m) => m.Family) },
  { path: 'setup', title: 'Anioł Stróż · Ustawienia', canActivate: [notOnStart], loadComponent: () => import('../pages/setup/setup').then((m) => m.Setup) },
  { path: 'audit', title: 'Anioł Stróż · Audyt', loadComponent: () => import('../pages/audit/audit').then((m) => m.Audit) },
];

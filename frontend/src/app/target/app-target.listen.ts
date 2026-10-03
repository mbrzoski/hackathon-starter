import { Routes } from '@angular/router';

// "Nasłuch" app: the tablet next to the landline. See app-target.ts.
export const APP_HOME = 'listen';

export const APP_CONNECTION_BAR = true;

export const APP_ROUTES: Routes = [
  { path: 'listen', title: 'Anioł Stróż · Nasłuch', loadComponent: () => import('../pages/listen/listen').then((m) => m.Listen) },
];

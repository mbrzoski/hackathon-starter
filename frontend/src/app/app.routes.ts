import { Routes } from '@angular/router';
import { APP_HOME, APP_ROUTES } from './target/app-target';

// The routes come from the build target (senior, listen or family app). The first path segment also selects
// the /ws/events role (see EventsService).
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: APP_HOME },
  ...APP_ROUTES,
  { path: '**', redirectTo: APP_HOME },
];

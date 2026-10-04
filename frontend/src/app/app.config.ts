import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideApi } from './api/provide-api';
import { backendLocation } from './core/backend-origin';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // Generated services call the same origin; nginx/Caddy proxies /api to the backend (WEB-02). Android: AND-02.
    provideApi(backendLocation().origin),
  ],
};

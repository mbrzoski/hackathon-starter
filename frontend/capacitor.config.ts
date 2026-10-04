import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Android shell (AND-01): the same Angular app, built by scripts/build-android.sh into dist/android-web with the
 * backend address baked in (AND-02). The page runs at https://localhost inside the WebView; the backend allows that
 * origin (the demo already lists it in PUBLIC_ORIGIN). No cleartext traffic.
 */
const config: CapacitorConfig = {
  appId: 'pl.aniolstroz.app',
  appName: 'Anioł Stróż',
  webDir: 'dist/android-web/browser',
  server: { androidScheme: 'https' },
  android: { allowMixedContent: false },
};

export default config;

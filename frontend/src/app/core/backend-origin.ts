/** Set only in an Android build: `ng build --define ANIOL_BACKEND_ORIGIN="'https://192.168.1.50'"` (AND-02). */
declare const ANIOL_BACKEND_ORIGIN: string | undefined;

export type BackendLocation = Pick<Location, 'protocol' | 'host' | 'origin'>;

/**
 * Where the backend is. On the web the apps and the backend share one origin behind the proxy (WEB-02), so it is the
 * page's own. An Android build loads the app from the APK and talks to the address it was built with, always over
 * https/wss (AND-02).
 */
export function backendLocation(): BackendLocation {
  const configured = typeof ANIOL_BACKEND_ORIGIN === 'string' && ANIOL_BACKEND_ORIGIN ? new URL(ANIOL_BACKEND_ORIGIN) : null;
  if (configured) {
    return { protocol: configured.protocol, host: configured.host, origin: configured.origin };
  }
  return { protocol: location.protocol, host: location.host, origin: location.origin };
}

/** What the screen shows when audio cannot run. `setupLink`: offer the way to /setup (missing consent). */
export interface AudioError {
  message: string;
  setupLink: boolean;
}

export const INSECURE_CONTEXT: AudioError = {
  message: 'Mikrofon działa tylko przez połączenie szyfrowane (HTTPS). Otwórz Anioła Stróża pod adresem https://.',
  setupLink: false,
};

export const NO_CONSENT: AudioError = {
  message: 'Brak zgody na nasłuch. Dokończ konfigurację, zanim włączysz ochronę.',
  setupLink: true,
};

/** The browser only starts audio after a tap; the automatic start of the Nasłuch screen was blocked. */
export const NEEDS_TAP: AudioError = {
  message: 'Przeglądarka wymaga jednego dotknięcia, żeby uruchomić mikrofon. Dotknij „Włącz ochronę”.',
  setupLink: false,
};

export const CONNECTION_LOST: AudioError = {
  message: 'Połączenie z Aniołem Stróżem zostało przerwane. Ochrona nie działa.',
  setupLink: false,
};

/** Maps the error of getUserMedia / AudioContext to a Polish message (never silent, rule 7). */
export function microphoneError(error: unknown): AudioError {
  const name = error instanceof Error ? error.name : '';
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
    case 'PermissionDeniedError':
      return {
        message: 'Brak dostępu do mikrofonu. Zezwól na mikrofon w ustawieniach przeglądarki i spróbuj ponownie.',
        setupLink: false,
      };
    case 'NotFoundError':
    case 'DevicesNotFoundError':
    case 'OverconstrainedError':
      return { message: 'Nie znaleziono mikrofonu. Podłącz mikrofon i spróbuj ponownie.', setupLink: false };
    case 'NotReadableError':
    case 'TrackStartError':
      return { message: 'Mikrofon jest zajęty przez inną aplikację. Zamknij ją i spróbuj ponownie.', setupLink: false };
    default:
      return { message: 'Nie udało się uruchomić mikrofonu. Spróbuj ponownie.', setupLink: false };
  }
}

/**
 * Maps the close of /ws/audio to a message. 1008 is a refusal: no consent (the reason of the backend starts with
 * "Brak zgody"), or another policy reason such as a call that is already running, which we show as it is.
 */
export function closeError(code: number, reason: string): AudioError {
  if (code === 1008) {
    return reason && !/zgody/i.test(reason) ? { message: reason, setupLink: false } : NO_CONSENT;
  }
  if (code === 1011 && reason) {
    return { message: reason, setupLink: false };
  }
  return CONNECTION_LOST;
}

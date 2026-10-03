import { HttpErrorResponse } from '@angular/common/http';

/** Text for the UI from an HTTP error: the ProblemDetail `detail` field (API-06), or a Polish fallback. */
export function problemDetailText(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const detail = (err.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
    return err.status === 0 ? 'Brak połączenia z serwerem.' : `Błąd serwera (${err.status}).`;
  }
  return 'Nieznany błąd.';
}

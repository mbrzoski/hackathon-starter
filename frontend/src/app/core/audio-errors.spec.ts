import { NO_CONSENT, closeError, microphoneError } from './audio-errors';

const named = (name: string) => Object.assign(new Error('x'), { name });

describe('microphoneError', () => {
  it('explains a refused permission', () => {
    expect(microphoneError(named('NotAllowedError')).message).toContain('Brak dostępu do mikrofonu');
  });

  it('explains a missing microphone', () => {
    expect(microphoneError(named('NotFoundError')).message).toContain('Nie znaleziono mikrofonu');
  });

  it('explains a microphone used by another app', () => {
    expect(microphoneError(named('NotReadableError')).message).toContain('zajęty');
  });

  it('never stays silent for an unknown error', () => {
    expect(microphoneError('boom').message).toContain('Nie udało się uruchomić mikrofonu');
  });
});

describe('closeError', () => {
  it('1008 with the consent reason of the backend offers /setup', () => {
    expect(closeError(1008, 'Brak zgody. Dokończ konfigurację.')).toEqual(NO_CONSENT);
    expect(closeError(1008, '').setupLink).toBe(true);
  });

  it('1008 for another reason shows that reason without the link', () => {
    const error = closeError(1008, 'Rozmowa już trwa. Zakończ ją i spróbuj ponownie.');
    expect(error.message).toContain('Rozmowa już trwa');
    expect(error.setupLink).toBe(false);
  });

  it('shows the reason of a server error, and a generic message otherwise', () => {
    expect(closeError(1011, 'Nie udało się uruchomić rozpoznawania mowy').message).toContain('rozpoznawania');
    expect(closeError(1006, '').message).toContain('przerwane');
  });
});

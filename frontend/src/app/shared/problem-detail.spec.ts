import { HttpErrorResponse } from '@angular/common/http';
import { problemDetailText } from './problem-detail';

describe('problemDetailText', () => {
  it('shows the ProblemDetail detail', () => {
    const err = new HttpErrorResponse({ status: 400, error: { title: 'Bad Request', detail: 'Nieprawidłowe żądanie.' } });
    expect(problemDetailText(err)).toBe('Nieprawidłowe żądanie.');
  });

  it('falls back when the backend is unreachable', () => {
    expect(problemDetailText(new HttpErrorResponse({ status: 0 }))).toBe('Brak połączenia z serwerem.');
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';
import { createResource, describeError } from './resource';

describe('createResource', () => {
  it('goes loading -> data', () => {
    const res = createResource<string>();
    const source = new Subject<string>();

    res.load(source);
    expect(res.loading()).toBeTrue();

    source.next('ok');
    expect(res.loading()).toBeFalse();
    expect(res.data()).toBe('ok');
    expect(res.error()).toBeNull();
  });

  it('exposes the backend error message and clears it on reset', () => {
    const res = createResource<string>();
    const body = { message: 'Validation failed', details: ['message: must not be blank'] };

    res.load(throwError(() => new HttpErrorResponse({ status: 400, error: body })));

    expect(res.error()).toBe('Validation failed (message: must not be blank)');
    res.reset();
    expect(res.error()).toBeNull();
  });

  it('describes an unreachable backend', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toBe('Cannot reach the backend');
  });
});

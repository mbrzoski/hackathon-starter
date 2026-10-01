import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiError } from './models';

/** Loading / error / data state for one async call, as signals. Enough state management for a hackathon. */
export interface Resource<T> {
  readonly data: Signal<T | null>;
  readonly loading: Signal<boolean>;
  readonly error: Signal<string | null>;
  /** Runs the call (again). Pass a new source to reuse the resource for a different request. */
  load(source: Observable<T>): void;
  reset(): void;
}

export function createResource<T>(): Resource<T> {
  const data = signal<T | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);

  return {
    data: data.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load(source: Observable<T>) {
      loading.set(true);
      error.set(null);
      source.subscribe({
        next: (value) => {
          data.set(value);
          loading.set(false);
        },
        error: (err: unknown) => {
          error.set(describeError(err));
          loading.set(false);
        },
      });
    },
    reset() {
      data.set(null);
      loading.set(false);
      error.set(null);
    },
  };
}

export function describeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) {
      return body.details?.length ? `${body.message} (${body.details.join('; ')})` : body.message;
    }
    return err.status === 0 ? 'Cannot reach the backend' : `Request failed (${err.status})`;
  }
  return err instanceof Error ? err.message : 'Unknown error';
}

import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { AlertsService } from '../api/api/alerts.service';
import { DecisionRequest, DecisionRequestDecisionEnum } from '../api/model/models';
import { DecisionOutbox } from './decision-outbox';

describe('DecisionOutbox', () => {
  let responses: Observable<unknown>[];
  let calls: { alertId: string; body: DecisionRequest }[];
  let outbox: DecisionOutbox;

  beforeEach(() => {
    vi.useFakeTimers();
    responses = [];
    calls = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AlertsService,
          useValue: {
            submitDecision: (alertId: string, body: DecisionRequest) => {
              calls.push({ alertId, body });
              return responses.shift() ?? of({});
            },
          },
        },
      ],
    });
    outbox = TestBed.inject(DecisionOutbox);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('posts the decision as the senior', () => {
    outbox.send('a1', DecisionRequestDecisionEnum.hung_up);
    expect(calls).toEqual([{ alertId: 'a1', body: { actor: 'senior', decision: 'hung_up' } }]);
  });

  it('retries network and server errors in the background with backoff', () => {
    responses = [
      throwError(() => new HttpErrorResponse({ status: 0 })),
      throwError(() => new HttpErrorResponse({ status: 503 })),
      of({}),
    ];
    outbox.send('a1', DecisionRequestDecisionEnum.false_alarm);
    expect(calls.length).toBe(1);

    vi.advanceTimersByTime(2000);
    expect(calls.length).toBe(2);
    vi.advanceTimersByTime(3999);
    expect(calls.length).toBe(2);
    vi.advanceTimersByTime(1);
    expect(calls.length).toBe(3);

    vi.advanceTimersByTime(60_000);
    expect(calls.length).toBe(3);
  });

  it('does not retry a rejected decision (4xx)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    responses = [throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'Nie znaleziono zasobu.' } }))];
    outbox.send('a1', DecisionRequestDecisionEnum.called_trusted);
    vi.advanceTimersByTime(60_000);
    expect(calls.length).toBe(1);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('Nie znaleziono zasobu.'));
  });

  it('reports a rejected decision as unsaved until acknowledged (FF-11)', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(outbox.unsaved()).toBe(false);
    responses = [throwError(() => new HttpErrorResponse({ status: 404 }))];
    outbox.send('a1', DecisionRequestDecisionEnum.hung_up);
    expect(outbox.unsaved()).toBe(true);

    outbox.acknowledge();
    expect(outbox.unsaved()).toBe(false);
  });

  it('gives up after 12 attempts (about 4 minutes) and reports the decision as unsaved (FF-11)', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    responses = Array.from({ length: 20 }, () => throwError(() => new HttpErrorResponse({ status: 503 })));
    outbox.send('a1', DecisionRequestDecisionEnum.hung_up);

    vi.advanceTimersByTime(30 * 60_000);
    expect(calls.length).toBe(12);
    expect(outbox.unsaved()).toBe(true);
  });

  it('stays quiet while a retry is still pending', () => {
    responses = [throwError(() => new HttpErrorResponse({ status: 0 }))];
    outbox.send('a1', DecisionRequestDecisionEnum.hung_up);
    expect(outbox.unsaved()).toBe(false);
    vi.advanceTimersByTime(2000);
    expect(outbox.unsaved()).toBe(false);
  });
});

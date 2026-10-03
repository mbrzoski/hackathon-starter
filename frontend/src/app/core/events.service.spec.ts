import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import contract from '../../../public/assets/contracts/openapi-schemas.json';
import { CONTRACT_LOADER, EventsService, WEB_SOCKET_FACTORY, eventsUrl, roleForUrl } from './events.service';

class FakeSocket {
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {}

  close(): void {
    this.closed = true;
  }

  serverOpen(): void {
    this.onopen?.();
  }

  serverSend(data: unknown): void {
    this.onmessage?.({ data: typeof data === 'string' ? data : JSON.stringify(data) });
  }

  serverClose(): void {
    this.onclose?.();
  }
}

const AT = '2026-10-04T10:00:00Z';

function event(type: string, payload: unknown, mode = 'SCRIPTED') {
  return { type, mode, at: AT, payload };
}

function segment(segId: string, text: string, isFinal = true) {
  return { callId: 'c1', segId, tStartMs: 0, tEndMs: 1000, text, isFinal, speaker: 'A' };
}

describe('EventsService', () => {
  let sockets: FakeSocket[];
  let service: EventsService;

  const lastSocket = () => sockets[sockets.length - 1];

  beforeEach(async () => {
    sockets = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: WEB_SOCKET_FACTORY,
          useValue: (url: string) => {
            const s = new FakeSocket(url);
            sockets.push(s);
            return s as unknown as WebSocket;
          },
        },
        { provide: CONTRACT_LOADER, useValue: async () => contract },
      ],
    });
    service = TestBed.inject(EventsService);
    await service.connect('family');
    lastSocket().serverOpen();
  });

  afterEach(() => {
    service.disconnect();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('connects with the role in the url and reports the connection', () => {
    expect(lastSocket().url).toMatch(/\/ws\/events\?role=family$/);
    expect(service.connection()).toBe('open');
  });

  it('parses valid events into signals', () => {
    const s = lastSocket();
    s.serverSend(event('call.started', { callId: 'c1' }));
    s.serverSend(event('transcript.segment', segment('s1', 'Dzień dobry, mówi komisarz')));
    s.serverSend(event('transcript.segment', segment('s1', 'Dzień dobry, mówi komisarz Nowak')));
    s.serverSend(
      event('risk.update', {
        callId: 'c1',
        level: 'medium',
        previousLevel: 'low',
        stages: ['AUTHORITY_CLAIM'],
        warningSigns: 1,
      }),
    );
    s.serverSend(event('system.status', { component: 'stt', state: 'down', message: 'Brak STT.', at: AT }));

    expect(service.mode()).toBe('SCRIPTED');
    expect(service.activeCall()?.callId).toBe('c1');
    expect(service.segments().map((x) => x.text)).toEqual(['Dzień dobry, mówi komisarz Nowak']);
    expect(service.risk()?.level).toBe('medium');
    expect(service.systemStatus().stt?.state).toBe('down');
  });

  it('rejects invalid messages with a console warning', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const s = lastSocket();

    s.serverSend('not json');
    s.serverSend(event('risk.update', { callId: 'c1', level: '82%' }));
    s.serverSend({ ...event('call.started', { callId: 'c1' }), extra: true });
    s.serverSend(event('unknown.type', {}));

    expect(warn).toHaveBeenCalledTimes(4);
    expect(service.risk()).toBeNull();
    expect(service.activeCall()).toBeNull();
    expect(service.mode()).toBeNull();
  });

  it('never logs the content of a rejected message, only its type and the errors (FF-09)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const s = lastSocket();
    const secret = 'Proszę nikomu nie mówić o przelewie';

    s.serverSend(`${secret} (not json)`);
    s.serverSend({ ...event('transcript.segment', segment('s1', secret)), extra: true });

    expect(warn).toHaveBeenCalledTimes(2);
    const logged = JSON.stringify(warn.mock.calls);
    expect(logged).not.toContain(secret);
    expect(logged).toContain('transcript.segment');
  });

  it('clears the previous call on call.started', () => {
    const s = lastSocket();
    s.serverSend(event('call.started', { callId: 'c1' }));
    s.serverSend(event('transcript.segment', segment('s1', 'Proszę nikomu nie mówić')));
    s.serverSend(
      event('alert.created', {
        alertId: 'a1',
        callId: 'c1',
        level: 'high',
        stages: [],
        templateId: 't1',
        shortText: 'Ta rozmowa może być oszustwem.',
        advice: 'Rozłącz się.',
        triggeredBy: 'both',
        createdAt: AT,
        mode: 'SCRIPTED',
      }),
    );
    s.serverSend(event('alert.decision', { alertId: 'a1', actor: 'senior', decision: 'hung_up', at: AT }));
    s.serverSend(event('call.ended', { callId: 'c1', hadAlert: true }));
    expect(service.activeCall()?.hadAlert).toBe(true);

    s.serverSend(event('call.started', { callId: 'c2' }));

    expect(service.activeCall()).toEqual({ callId: 'c2', startedAt: AT, endedAt: null, hadAlert: null });
    expect(service.segments()).toEqual([]);
    expect(service.alerts()).toEqual([]);
    expect(service.decisions()).toEqual([]);
    expect(service.risk()).toBeNull();
  });

  it('keeps the call state when call.started repeats the current callId (reconnect snapshot)', () => {
    const s = lastSocket();
    s.serverSend(event('call.started', { callId: 'c1' }));
    s.serverSend(event('transcript.segment', segment('s1', 'Proszę nikomu nie mówić')));
    s.serverSend(
      event('risk.update', { callId: 'c1', level: 'high', previousLevel: 'medium', stages: [], warningSigns: 2 }),
    );
    s.serverSend(
      event('alert.created', {
        alertId: 'a1',
        callId: 'c1',
        level: 'high',
        stages: [],
        templateId: 't1',
        shortText: 'Ta rozmowa może być oszustwem.',
        advice: 'Rozłącz się.',
        triggeredBy: 'both',
        createdAt: AT,
        mode: 'SCRIPTED',
      }),
    );
    s.serverSend(event('alert.decision', { alertId: 'a1', actor: 'senior', decision: 'hung_up', at: AT }));

    s.serverSend(event('call.started', { callId: 'c1' }));

    expect(service.activeCall()?.callId).toBe('c1');
    expect(service.segments().map((x) => x.text)).toEqual(['Proszę nikomu nie mówić']);
    expect(service.risk()?.level).toBe('high');
    expect(service.alerts().map((a) => a.alertId)).toEqual(['a1']);
    expect(service.decisions().map((d) => d.decision)).toEqual(['hung_up']);
  });

  it('reconnects with backoff capped at 10 s', () => {
    vi.useFakeTimers();
    const delays: number[] = [];
    for (let i = 0; i < 6; i++) {
      const before = sockets.length;
      lastSocket().serverClose();
      expect(service.connection()).toBe('closed');
      let waited = 0;
      while (sockets.length === before) {
        vi.advanceTimersByTime(500);
        waited += 500;
      }
      delays.push(waited);
    }
    expect(delays).toEqual([1000, 2000, 4000, 8000, 10000, 10000]);
    expect(lastSocket().url).toContain('role=family');

    lastSocket().serverOpen();
    expect(service.connection()).toBe('open');
    lastSocket().serverClose();
    vi.advanceTimersByTime(1000);
    expect(sockets.length).toBe(8);
  });

  it('does not reconnect after an explicit disconnect', () => {
    vi.useFakeTimers();
    const count = sockets.length;
    service.disconnect();
    vi.advanceTimersByTime(20_000);
    expect(sockets.length).toBe(count);
  });
});

describe('events url helpers', () => {
  it('maps routes to roles', () => {
    expect(roleForUrl('/senior')).toBe('senior');
    expect(roleForUrl('/listen')).toBe('senior');
    expect(roleForUrl('/family?x=1')).toBe('family');
    expect(roleForUrl('/setup')).toBe('family');
    expect(roleForUrl('/audit')).toBe('audit');
    expect(roleForUrl('/nope')).toBeNull();
  });

  it('builds ws: or wss: from the page location', () => {
    expect(eventsUrl('senior', { protocol: 'https:', host: 'demo.example:8443' })).toBe(
      'wss://demo.example:8443/ws/events?role=senior',
    );
    expect(eventsUrl('audit', { protocol: 'http:', host: 'localhost:4200' })).toBe(
      'ws://localhost:4200/ws/events?role=audit',
    );
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  BACKEND_STARTED_AT,
  EventsService,
  WEB_SOCKET_FACTORY,
  eventsUrl,
  roleForUrl,
} from './events.service';

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
  let backendStart: string;
  let statusFails: boolean;

  const lastSocket = () => sockets[sockets.length - 1];

  beforeEach(async () => {
    sockets = [];
    backendStart = '2026-10-04T09:00:00Z';
    statusFails = false;
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
        {
          provide: BACKEND_STARTED_AT,
          useValue: async () => {
            if (statusFails) throw new Error('status unavailable');
            return backendStart;
          },
        },
      ],
    });
    service = TestBed.inject(EventsService);
    service.connect('family');
    lastSocket().serverOpen();
    await new Promise((r) => setTimeout(r, 0)); // first GET /api/status
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

  it('keeps the mode of the running call when a system status of another mode arrives (rule 6)', () => {
    const s = lastSocket();
    s.serverSend(event('call.started', { callId: 'c1' }, 'LIVE'));
    s.serverSend(event('system.status', { component: 'backend', state: 'ok', message: 'Backend działa.', at: AT }, 'SCRIPTED'));
    expect(service.mode()).toBe('LIVE');

    s.serverSend(event('transcript.segment', segment('s1', 'Dzień dobry'), 'LIVE'));
    s.serverSend(event('system.status', { component: 'backend', state: 'ok', message: 'Backend działa.', at: AT }, 'SCRIPTED'));
    expect(service.mode()).toBe('LIVE');
  });

  it('takes the mode of a system status again when no call is running', () => {
    const s = lastSocket();
    s.serverSend(event('call.started', { callId: 'c1' }, 'LIVE'));
    s.serverSend(event('call.ended', { callId: 'c1', hadAlert: false }, 'LIVE'));
    s.serverSend(event('system.status', { component: 'backend', state: 'ok', message: 'Backend działa.', at: AT }, 'SCRIPTED'));
    expect(service.mode()).toBe('SCRIPTED');
  });

  it('follows the simulated phone call and forgets it when the connection restarts', () => {
    const s = lastSocket();
    expect(service.phoneCall()).toBeNull();
    s.serverSend(event('phone.call', { active: true, number: '+48 600 100 200' }));
    expect(service.phoneCall()).toEqual({ active: true, number: '+48 600 100 200' });
    s.serverSend(event('phone.call', { active: false, number: '+48 600 100 200' }));
    expect(service.phoneCall()).toBeNull();

    s.serverSend(event('phone.call', { active: true, number: '+48 600 100 200' }));
    vi.useFakeTimers();
    s.serverClose();
    vi.advanceTimersByTime(1000);
    expect(service.phoneCall()).toBeNull();
  });

  it('is recording only while the phone call is on and the audio path came up after it began', () => {
    const s = lastSocket();
    const audio = (state: string, at: string) =>
      event('system.status', { component: 'audio', state, message: 'x', at });
    expect(service.recording()).toBe(false);

    // An "ok" left over from an earlier session does not count.
    s.serverSend(audio('ok', '2026-10-04T09:00:00Z'));
    s.serverSend({ type: 'phone.call', mode: 'SCRIPTED', at: '2026-10-04T10:00:00Z', payload: { active: true, number: '+48 600 100 200' } });
    expect(service.recording()).toBe(false);

    s.serverSend(audio('ok', '2026-10-04T10:00:03Z'));
    expect(service.recording()).toBe(true);

    s.serverSend(audio('down', '2026-10-04T10:00:20Z'));
    expect(service.recording()).toBe(false);

    s.serverSend(audio('ok', '2026-10-04T10:00:30Z'));
    expect(service.recording()).toBe(true);
    s.serverSend({ type: 'phone.call', mode: 'SCRIPTED', at: '2026-10-04T10:01:00Z', payload: { active: false, number: '+48 600 100 200' } });
    expect(service.recording()).toBe(false);
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

  describe('backend restart during a call (UC-01 test, bug 1b)', () => {
    const flush = () => new Promise((r) => setTimeout(r, 0));
    async function reconnect(newStart: string) {
      backendStart = newStart;
      vi.useFakeTimers();
      lastSocket().serverClose();
      vi.advanceTimersByTime(1000);
      vi.useRealTimers();
      lastSocket().serverOpen();
      await flush();
    }

    it('marks the ongoing call interrupted when the backend comes back with a new start time', async () => {
      lastSocket().serverSend(event('call.started', { callId: 'c1' }));
      await reconnect('2026-10-04T11:00:00Z');

      const call = service.activeCall();
      expect(call?.callId).toBe('c1');
      expect(call?.interrupted).toBe(true);
      expect(call?.endedAt).not.toBeNull();
    });

    it('keeps the call after a reconnect to the same backend', async () => {
      lastSocket().serverSend(event('call.started', { callId: 'c1' }));
      await reconnect(backendStart);
      expect(service.activeCall()).toEqual({ callId: 'c1', startedAt: AT, endedAt: null, hadAlert: null });
    });

    it('leaves a new call from the restarted backend alone', async () => {
      lastSocket().serverSend(event('call.started', { callId: 'c1' }));
      backendStart = '2026-10-04T11:00:00Z';
      vi.useFakeTimers();
      lastSocket().serverClose();
      vi.advanceTimersByTime(1000);
      vi.useRealTimers();
      lastSocket().serverOpen();
      lastSocket().serverSend(event('call.started', { callId: 'c2' }));
      await flush();
      expect(service.activeCall()).toEqual({ callId: 'c2', startedAt: AT, endedAt: null, hadAlert: null });
    });

    it('does nothing when the status cannot be read', async () => {
      lastSocket().serverSend(event('call.started', { callId: 'c1' }));
      statusFails = true;
      await reconnect('2026-10-04T11:00:00Z');
      expect(service.activeCall()?.interrupted).toBeUndefined();
      expect(service.activeCall()?.endedAt).toBeNull();
    });
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

  it('goes offline and reconnects when nothing arrives for 25 s on an open socket (half-open, FF-04)', () => {
    vi.useFakeTimers();
    const before = sockets.length;
    lastSocket().serverOpen(); // restarts the watch under fake timers
    vi.advanceTimersByTime(20_000);
    lastSocket().serverSend(event('system.status', { component: 'backend', state: 'ok', message: 'ok', at: AT }));
    vi.advanceTimersByTime(20_000);
    expect(service.connection()).toBe('open');

    vi.advanceTimersByTime(5_001); // 25 s since the last message
    expect(service.connection()).toBe('closed');
    expect(sockets[sockets.length - 1].closed).toBe(true);
    vi.advanceTimersByTime(1_000);
    expect(sockets.length).toBe(before + 1);
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

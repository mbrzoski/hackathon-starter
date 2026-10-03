import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Alert, Decision, Mode, SystemStatus, TranscriptSegment } from '../../api/model/models';
import { AudioService } from '../../core/audio.service';
import { ActiveCall, ConnectionState, EventsService } from '../../core/events.service';
import { Listen } from './listen';

const AT = '2026-10-04T10:00:00Z';
const CALL: ActiveCall = { callId: 'c1', startedAt: AT, endedAt: null, hadAlert: null };
const ALERT = {
  alertId: 'a1', callId: 'c1', level: 'high', stages: [], templateId: 't', shortText: 'Możliwe oszustwo.',
  advice: 'Rozłącz się.', triggeredBy: 'keywords', createdAt: AT, mode: 'SCRIPTED',
} as unknown as Alert;

type AudioError = { message: string; setupLink: boolean };

describe('Listen', () => {
  const events = {
    connection: signal<ConnectionState>('open'),
    mode: signal<Mode | null>(Mode.SCRIPTED),
    systemStatus: signal<Partial<Record<string, SystemStatus>>>({}),
    activeCall: signal<ActiveCall | null>(null),
    segments: signal<TranscriptSegment[]>([]),
    alerts: signal<Alert[]>([]),
    decisions: signal<Decision[]>([]),
    risk: signal(null),
    online: () => events.connection() === 'open',
  };
  const audio = {
    start: vi.fn(async () => undefined as void),
    state: signal<string>('idle'),
    error: signal<AudioError | null>(null),
  };
  const heading = (f: ComponentFixture<Listen>) => (f.nativeElement as HTMLElement).querySelector('main h1')?.textContent?.trim();
  const el = (f: ComponentFixture<Listen>) => f.nativeElement as HTMLElement;

  /** The microphone works after the screen asked for it. */
  const microphoneWorks = () =>
    audio.start.mockImplementation(async () => {
      audio.state.set('listening');
      audio.error.set(null);
    });
  const microphoneFails = (error: AudioError) =>
    audio.start.mockImplementation(async () => {
      audio.state.set('error');
      audio.error.set(error);
    });

  async function open() {
    TestBed.configureTestingModule({
      imports: [Listen],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: AudioService, useValue: audio },
      ],
    });
    const fixture = TestBed.createComponent(Listen);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    audio.start.mockReset();
    microphoneWorks();
    audio.state.set('idle');
    audio.error.set(null);
    events.connection.set('open');
    events.activeCall.set(null);
    events.alerts.set([]);
    events.systemStatus.set({});
  });

  afterEach(() => vi.useRealTimers());

  it('starts the microphone by itself, without a tap, and then waits for a call', async () => {
    const fixture = await open();
    expect(audio.start).toHaveBeenCalledTimes(1);
    expect(heading(fixture)).toBe('Czekam na rozmowę');
    expect(el(fixture).querySelector('button')).toBeNull();
  });

  it('does not start while offline, and starts when the connection is back', async () => {
    events.connection.set('closed');
    const fixture = await open();
    expect(audio.start).not.toHaveBeenCalled();
    expect(heading(fixture)).toBe('Anioł Stróż jest offline');

    events.connection.set('open');
    await fixture.whenStable();
    expect(audio.start).toHaveBeenCalledTimes(1);
  });

  it('says why when the microphone does not start, and offers the tap as a fallback (rule 7)', async () => {
    microphoneFails({ message: 'Nie znaleziono mikrofonu.', setupLink: false });
    const fixture = await open();
    expect(heading(fixture)).toBe('Ochrona nie działa');
    expect(el(fixture).querySelector('.audio-error[role="alert"]')?.textContent).toContain('Nie znaleziono mikrofonu.');
    expect(el(fixture).querySelector('.audio-error a')).toBeNull();

    microphoneWorks();
    el(fixture).querySelector<HTMLButtonElement>('.start')!.click();
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });

  it('links to /setup when consent is missing', async () => {
    microphoneFails({ message: 'Brak zgody na nasłuch.', setupLink: true });
    const fixture = await open();
    expect(el(fixture).querySelector('.audio-error a')?.getAttribute('href')).toBe('/setup');
  });

  it('tries again after a failure without anyone tapping', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    microphoneFails({ message: 'Połączenie przerwane.', setupLink: false });
    const fixture = await open();
    expect(audio.start).toHaveBeenCalledTimes(1);

    microphoneWorks();
    await vi.advanceTimersByTimeAsync(5000);
    await fixture.whenStable();
    expect(audio.start).toHaveBeenCalledTimes(2);
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });

  it('has no pause: switching protection off is not a thing of this screen', async () => {
    const fixture = await open();
    events.activeCall.set(CALL);
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Słucham rozmowy');
    expect(el(fixture).querySelector('button')).toBeNull();
  });

  it('shows the alarm only while the call is going on', async () => {
    const fixture = await open();
    events.activeCall.set(CALL);
    events.alerts.set([ALERT]);
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Możliwe oszustwo.');

    events.activeCall.set({ ...CALL, endedAt: AT, hadAlert: true });
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });

  it('stops alarming when the backend lost the call (UC-01 test, bug 1b)', async () => {
    const fixture = await open();
    events.activeCall.set(CALL);
    events.alerts.set([ALERT]);
    await fixture.whenStable();

    events.activeCall.set({ ...CALL, endedAt: AT, interrupted: true });
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });
});

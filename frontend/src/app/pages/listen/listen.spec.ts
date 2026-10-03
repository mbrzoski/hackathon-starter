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
  const heading = (f: ComponentFixture<Listen>) => (f.nativeElement as HTMLElement).querySelector('main h1')?.textContent?.trim();

  async function armed() {
    TestBed.configureTestingModule({
      imports: [Listen],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: AudioService, useValue: { start: () => undefined, pause: () => undefined, resume: () => undefined } },
      ],
    });
    const fixture = TestBed.createComponent(Listen);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.start')!.click();
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    events.connection.set('open');
    events.activeCall.set(null);
    events.alerts.set([]);
  });

  it('shows the alarm only while the call is going on', async () => {
    const fixture = await armed();
    events.activeCall.set(CALL);
    events.alerts.set([ALERT]);
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Możliwe oszustwo.');

    events.activeCall.set({ ...CALL, endedAt: AT, hadAlert: true });
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });

  it('stops alarming when the backend lost the call (UC-01 test, bug 1b)', async () => {
    const fixture = await armed();
    events.activeCall.set(CALL);
    events.alerts.set([ALERT]);
    await fixture.whenStable();

    events.activeCall.set({ ...CALL, endedAt: AT, interrupted: true });
    await fixture.whenStable();
    expect(heading(fixture)).toBe('Czekam na rozmowę');
  });
});

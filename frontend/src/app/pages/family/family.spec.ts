import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Observable, defer, of, throwError } from 'rxjs';
import { AlertsService } from '../../api/api/alerts.service';
import { DataService } from '../../api/api/data.service';
import { DemoService } from '../../api/api/demo.service';
import { SettingsService } from '../../api/api/settings.service';
import { provideRouter } from '@angular/router';
import {
  Alert,
  AlertWithDecisions,
  Decision,
  DecisionRequest,
  Mode,
  SystemStatus,
  TranscriptSegment,
} from '../../api/model/models';
import { ActiveCall, ConnectionState, EventsService } from '../../core/events.service';
import { SettingsStore } from '../../core/settings.store';
import { AlertNotifier } from './alert-notifier';
import { stagesInOrder } from './evidence';
import { ALERT_TITLE, FAMILY_TITLE } from './family-feed';
import { Family } from './family';

const T0 = '2026-10-04T10:00:00Z';

function fakeEvents() {
  return {
    connection: signal<ConnectionState>('open'),
    mode: signal<Mode | null>(Mode.SCRIPTED),
    systemStatus: signal<Partial<Record<string, SystemStatus>>>({}),
    activeCall: signal<ActiveCall | null>(null),
    segments: signal<TranscriptSegment[]>([]),
    alerts: signal<Alert[]>([]),
    decisions: signal<Decision[]>([]),
    phoneCall: signal<{ active: boolean; number: string } | null>(null),
  };
}

function alert(id: string, createdAt: string, level = 'high', callId = 'c1'): Alert {
  return {
    alertId: id,
    callId,
    level,
    stages: [
      { stage: 'MONEY_REQUEST', segId: 's5', quote: 'wyplacic gotowke', speakerRole: 'caller', source: 'keywords', validated: true },
      { stage: 'SECRECY_DEMAND', segId: 's3', quote: 'Proszę nikomu nie mówić', speakerRole: 'caller', source: 'llm', validated: true },
    ],
    templateId: 't',
    shortText: `Alert ${id}`,
    advice: 'Odczekaj minutę.',
    triggeredBy: 'both',
    createdAt,
    mode: 'SCRIPTED',
  } as unknown as Alert;
}

function seg(segId: string, sec: number, text: string): TranscriptSegment {
  return { callId: 'c1', segId, tStartMs: sec * 1000, tEndMs: sec * 1000 + 900, text, isFinal: true, speaker: 'A' } as TranscriptSegment;
}

const SEGMENTS = [
  seg('s0', 1, 'Halo?'),
  seg('s1', 4, 'Dzień dobry, mówi komisarz.'),
  seg('s2', 9, 'Coś się stało?'),
  seg('s3', 18, 'Tak. Proszę nikomu nie mówić, to tajna akcja.'),
  seg('s4', 25, 'Dobrze.'),
  seg('s5', 37, 'Musi pani wypłacić gotówkę jeszcze dziś.'),
  seg('s6', 44, 'Ile?'),
  seg('s7', 50, 'Wszystko.'),
  seg('s8', 58, 'Rozumiem.'),
];

describe('Family', () => {
  let events: ReturnType<typeof fakeEvents>;
  let history: Observable<AlertWithDecisions[]>;
  let posted: { alertId: string; body: DecisionRequest }[];
  let notifier: { beep: ReturnType<typeof vi.fn> };
  let submit: () => Observable<unknown>;
  let demo: { setPhoneCall: ReturnType<typeof vi.fn>; stopReplay: ReturnType<typeof vi.fn> };
  let data: { deleteCallData: ReturnType<typeof vi.fn> };
  let settingsApi: { getSettings: ReturnType<typeof vi.fn>; setSettings: ReturnType<typeof vi.fn> };

  const el = (f: ComponentFixture<Family>) => f.nativeElement as HTMLElement;
  const text = (e: Element | null) => e?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const cards = (f: ComponentFixture<Family>) => [...el(f).querySelectorAll('app-alert-card')];
  const button = (root: Element, label: string) =>
    [...root.querySelectorAll('button')].find((b) => b.textContent?.includes(label)) as HTMLButtonElement;

  async function render() {
    const fixture = TestBed.createComponent(Family);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    events = fakeEvents();
    history = of([]);
    posted = [];
    submit = () => of({});
    notifier = { beep: vi.fn() };
    demo = { setPhoneCall: vi.fn(() => of({})), stopReplay: vi.fn(() => of({})) };
    data = { deleteCallData: vi.fn(() => of({})) };
    settingsApi = { getSettings: vi.fn(() => of({ seniorConsent: true, familyConsent: true, contacts: [], sensitivity: 'standard', retentionDays: 30, seniorName: '' })), setSettings: vi.fn() };
    TestBed.configureTestingModule({
      imports: [Family],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: AlertNotifier, useValue: notifier },
        { provide: DemoService, useValue: demo },
        { provide: SettingsService, useValue: settingsApi },
        { provide: DataService, useValue: data },
        provideRouter([]),
        {
          provide: AlertsService,
          useValue: {
            listAlerts: () => history,
            submitDecision: (alertId: string, body: DecisionRequest) => {
              posted.push({ alertId, body: JSON.parse(JSON.stringify(body)) });
              return submit();
            },
          },
        },
      ],
    });
  });

  it('shows history from GET /api/alerts and puts live alerts on top', async () => {
    history = of([
      { alert: alert('old', '2026-10-01T10:00:00Z', 'medium'), decisions: [] },
      { alert: alert('older', '2026-09-30T10:00:00Z', 'low'), decisions: [] },
    ] as AlertWithDecisions[]);
    const fixture = await render();
    expect(cards(fixture).map((c) => text(c.querySelector('h2')))).toEqual(['Alert old', 'Alert older']);

    events.alerts.set([alert('live', T0)]);
    await fixture.whenStable();
    expect(cards(fixture).map((c) => text(c.querySelector('h2')))).toEqual(['Alert live', 'Alert old', 'Alert older']);
    expect(text(cards(fixture)[0])).toContain('Wysokie');
    expect(text(cards(fixture)[0])).toContain('AI + słowa kluczowe');
  });

  it('rings and changes the tab title only for a new live high alert, until the family decides', async () => {
    history = of([{ alert: alert('old', '2026-10-01T10:00:00Z'), decisions: [] }] as AlertWithDecisions[]);
    const fixture = await render();
    const title = TestBed.inject(Title);
    expect(notifier.beep).not.toHaveBeenCalled();
    expect(title.getTitle()).toBe(FAMILY_TITLE);

    events.alerts.set([alert('m', T0, 'medium')]);
    await fixture.whenStable();
    expect(notifier.beep).not.toHaveBeenCalled();

    events.alerts.set([alert('m', T0, 'medium'), alert('h', '2026-10-04T10:01:00Z')]);
    await fixture.whenStable();
    expect(notifier.beep).toHaveBeenCalledTimes(1);
    expect(title.getTitle()).toBe(ALERT_TITLE);

    events.decisions.set([{ alertId: 'h', actor: 'family', decision: 'confirmed_scam', at: T0 } as unknown as Decision]);
    await fixture.whenStable();
    expect(title.getTitle()).toBe(FAMILY_TITLE);
  });

  it('highlights quotes where they were said, with and without Polish characters, with ±2 segments of context', async () => {
    events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: null, hadAlert: null });
    events.segments.set(SEGMENTS);
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();

    const marks = [...el(fixture).querySelectorAll('mark')].map((m) => m.textContent);
    expect(marks).toEqual(['Proszę nikomu nie mówić', 'wypłacić gotówkę']);

    const excerpt = el(fixture).querySelector('app-transcript-excerpt')!;
    const shown = [...excerpt.querySelectorAll('.row p')].map((p) => text(p));
    // s1..s7: s3 and s5 with two segments either side; s0 and s8 are outside the context.
    expect(shown).toEqual(SEGMENTS.slice(1, 8).map((s) => s.text));
  });

  it('lists the stages in the order they were said, with Polish names and times', async () => {
    events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: null, hadAlert: null });
    events.segments.set(SEGMENTS);
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();

    const items = [...el(fixture).querySelectorAll('app-stage-timeline li')].map((li) => text(li));
    expect(items[0]).toContain('Prośba o tajemnicę');
    expect(items[0]).toContain('00:18 · AI');
    expect(items[1]).toContain('Żądanie pieniędzy');
    expect(items[1]).toContain('00:37 · słowa kluczowe');
    expect(stagesInOrder(alert('x', T0), []).map((h) => h.segId)).toEqual(['s3', 's5']);
  });

  it('sends the family decision with actor family and the ignored stages', async () => {
    events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: null, hadAlert: null });
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();
    const card = cards(fixture)[0];

    const toggle = card.querySelectorAll<HTMLInputElement>('app-stage-timeline input[type=checkbox]')[0];
    toggle.click();
    await fixture.whenStable();
    button(card, 'Potwierdzam oszustwo').click();
    await fixture.whenStable();

    expect(posted).toEqual([
      { alertId: 'a1', body: { actor: 'family', decision: 'confirmed_scam', ignoredStages: ['SECRECY_DEMAND'] } },
    ]);
    expect(text(card)).toContain('Zapisuję decyzję…');
    expect(button(card, 'Fałszywy alarm').disabled).toBe(true);
  });

  it('sends "Fałszywy alarm" without ignored stages', async () => {
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();
    button(cards(fixture)[0], 'Fałszywy alarm').click();
    expect(posted).toEqual([{ alertId: 'a1', body: { actor: 'family', decision: 'false_alarm' } }]);
  });

  it('updates the card when alert.decision arrives', async () => {
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();
    expect(text(cards(fixture)[0])).not.toContain('wybrała');

    events.decisions.set([{ alertId: 'a1', actor: 'senior', decision: 'hung_up', at: '2026-10-04T10:02:00Z' } as unknown as Decision]);
    await fixture.whenStable();
    expect(text(cards(fixture)[0])).toMatch(/Senior wybrał\(a\): rozłączam się · \d\d:\d\d/);

    events.decisions.update((list) => [
      ...list,
      { alertId: 'a1', actor: 'family', decision: 'confirmed_scam', at: '2026-10-04T10:03:00Z' } as unknown as Decision,
    ]);
    await fixture.whenStable();
    const card = cards(fixture)[0];
    expect(text(card)).toContain('Rodzina: potwierdzone oszustwo');
    expect(button(card, 'Potwierdzam oszustwo').disabled).toBe(true);
    expect(card.querySelector('app-stage-timeline input')).toBeNull();
  });

  it('uses the name from settings in the senior choice and a neutral text without it (FF-17)', async () => {
    events.alerts.set([alert('a1', T0)]);
    events.decisions.set([{ alertId: 'a1', actor: 'senior', decision: 'hung_up', at: T0 } as unknown as Decision]);
    TestBed.inject(SettingsStore).senior.set({ name: 'Mama', phone: '' });
    const fixture = await render();
    expect(text(cards(fixture)[0])).toContain('Mama wybrał(a): rozłączam się');
  });

  it('offers "nie licz tego etapu" only for alerts of the ongoing call (FF-14)', async () => {
    history = of([{ alert: alert('old', '2026-10-01T10:00:00Z', 'high', 'c0'), decisions: [] }] as AlertWithDecisions[]);
    events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: null, hadAlert: null });
    events.alerts.set([alert('live', T0)]);
    const fixture = await render();
    const [live, old] = cards(fixture);
    expect(live.querySelector('app-stage-timeline input')).toBeTruthy();
    expect(old.querySelector('app-stage-timeline input')).toBeNull();

    events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: '2026-10-04T10:05:00Z', hadAlert: true });
    await fixture.whenStable();
    expect(cards(fixture)[0].querySelector('app-stage-timeline input')).toBeNull();
  });

  it('shows the reason and enables the buttons again when the decision is rejected (FF-14)', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    submit = () => throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Rozmowa już się zakończyła.' } }));
    events.alerts.set([alert('a1', T0)]);
    const fixture = await render();
    const card = cards(fixture)[0];
    button(card, 'Potwierdzam oszustwo').click();
    await fixture.whenStable();

    expect(text(card.querySelector('[role=alert]'))).toContain('Rozmowa już się zakończyła.');
    expect(text(card)).not.toContain('Zapisuję decyzję…');
    expect(button(card, 'Potwierdzam oszustwo').disabled).toBe(false);
  });

  it('reloads history when the connection comes back (FF-16)', async () => {
    let loads = 0;
    history = defer(() => {
      loads++;
      return of([]);
    });
    const fixture = await render();
    expect(loads).toBe(1);

    events.connection.set('closed');
    await fixture.whenStable();
    expect(loads).toBe(1);
    events.connection.set('open');
    await fixture.whenStable();
    expect(loads).toBe(2);
  });

  it('offers the call link only with a number from settings (FE-10)', async () => {
    events.alerts.set([alert('a1', T0)]);
    let fixture = await render();
    expect(el(fixture).querySelector('a[href^="tel:"]')).toBeNull();
    expect(text(el(fixture))).toContain('Numer seniora nie jest zapisany w ustawieniach.');

    TestBed.inject(SettingsStore).senior.set({ name: 'Mama', phone: '+48601000111' });
    fixture = await render();
    const link = el(fixture).querySelector('a[href^="tel:"]');
    expect(link?.getAttribute('href')).toBe('tel:+48601000111');
    expect(text(link)).toBe('Zadzwoń do: Mama');
  });

  it('shows the same protection states as the senior screen in a compact bar', async () => {
    events.connection.set('closed');
    const fixture = await render();
    expect(text(el(fixture).querySelector('app-system-status-bar'))).toBe('Anioł Stróż jest offline');

    events.connection.set('open');
    events.systemStatus.set({ ai: { component: 'ai', state: 'down', message: '', at: T0 } as unknown as SystemStatus });
    await fixture.whenStable();
    expect(text(el(fixture).querySelector('app-system-status-bar'))).toBe('Podstawowa ochrona (bez AI)');
  });

  it('shows the ProblemDetail text when the history cannot be loaded', async () => {
    history = throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Błąd serwera.' } }));
    const fixture = await render();
    expect(text(el(fixture).querySelector('.error'))).toContain('Błąd serwera.');
  });

  describe('simulated call', () => {
    const toggle = (f: ComponentFixture<Family>) => el(f).querySelector<HTMLButtonElement>('.simulate button')!;

    it('switches the simulated call on with a tap, and only then', async () => {
      const fixture = await render();
      expect(demo.setPhoneCall).not.toHaveBeenCalled();
      expect(text(toggle(fixture))).toBe('Zasymuluj połączenie');

      toggle(fixture).click();
      await fixture.whenStable();

      expect(demo.setPhoneCall).toHaveBeenCalledWith({ active: true });
    });

    it('switches it off again while it is on, on every screen it shows the same state', async () => {
      const fixture = await render();
      events.phoneCall.set({ active: true, number: '+48 600 100 200' });
      await fixture.whenStable();
      expect(text(toggle(fixture))).toBe('Zakończ symulację połączenia');
      expect(text(el(fixture).querySelector('.simulate .note'))).toContain('+48 600 100 200');

      toggle(fixture).click();
      await fixture.whenStable();

      expect(demo.setPhoneCall).toHaveBeenCalledWith({ active: false });
    });

    it('says so when the switch did not reach the backend (never silent)', async () => {
      demo.setPhoneCall.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const fixture = await render();

      toggle(fixture).click();
      await fixture.whenStable();

      expect(text(el(fixture).querySelector('.simulate [role="alert"]'))).toContain('Nie udało się zmienić symulacji');
    });
  });

  describe('consent', () => {
    it('asks to finish the setup when the consents are missing (AUD-07)', async () => {
      settingsApi.getSettings.mockReturnValue(
        of({ seniorConsent: false, familyConsent: true, contacts: [], sensitivity: 'standard', retentionDays: 30, seniorName: '' }),
      );
      const fixture = await render();
      const banner = el(fixture).querySelector('.consent[role="alert"]');
      expect(text(banner)).toContain('Brak zgód seniora i rodziny');
      expect(banner?.querySelector('a')?.getAttribute('href')).toBe('/setup');
    });

    it('says nothing when both consents are given', async () => {
      const fixture = await render();
      expect(el(fixture).querySelector('.consent')).toBeNull();
    });
  });

  describe('erasing the data of one call', () => {
    const erase = (card: Element) => button(card, 'Usuń dane tego połączenia');

    it('asks once more, erases the call on the backend and removes all its cards', async () => {
      history = of([
        { alert: alert('a1', '2026-10-01T10:00:00Z', 'high', 'call-1'), decisions: [] },
        { alert: alert('a2', '2026-10-01T10:05:00Z', 'medium', 'call-1'), decisions: [] },
        { alert: alert('b1', '2026-10-02T10:00:00Z', 'high', 'call-2'), decisions: [] },
      ] as AlertWithDecisions[]);
      const fixture = await render();
      expect(cards(fixture)).toHaveLength(3);
      const call1 = cards(fixture).find((c) => text(c.querySelector('h2')) === 'Alert a1')!;

      erase(call1).click();
      await fixture.whenStable();
      expect(data.deleteCallData).not.toHaveBeenCalled();
      expect(text(call1)).toContain('Tego nie da się cofnąć.');

      button(call1, 'Tak, usuń').click();
      await fixture.whenStable();

      expect(data.deleteCallData).toHaveBeenCalledWith('call-1');
      expect(cards(fixture).map((c) => text(c.querySelector('h2')))).toEqual(['Alert b1']);
      expect(text(el(fixture).querySelector('.deleted'))).toBe('Usunięto dane połączenia.');
    });

    it('"Anuluj" erases nothing', async () => {
      history = of([{ alert: alert('a1', T0, 'high', 'call-1'), decisions: [] }] as AlertWithDecisions[]);
      const fixture = await render();
      erase(cards(fixture)[0]).click();
      await fixture.whenStable();
      button(cards(fixture)[0], 'Anuluj').click();
      await fixture.whenStable();
      expect(data.deleteCallData).not.toHaveBeenCalled();
      expect(cards(fixture)).toHaveLength(1);
    });

    it('is not offered for the call that is still going on', async () => {
      events.alerts.set([alert('live', T0, 'high', 'c1')]);
      events.activeCall.set({ callId: 'c1', startedAt: T0, endedAt: null, hadAlert: true });
      const fixture = await render();
      expect(erase(cards(fixture)[0])).toBeUndefined();
    });

    it('says so when the backend refuses, and keeps the cards', async () => {
      data.deleteCallData.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      history = of([{ alert: alert('a1', T0, 'high', 'call-1'), decisions: [] }] as AlertWithDecisions[]);
      const fixture = await render();
      erase(cards(fixture)[0]).click();
      await fixture.whenStable();
      button(cards(fixture)[0], 'Tak, usuń').click();
      await fixture.whenStable();
      expect(text(el(fixture).querySelector('main > .error[role="alert"]'))).toContain('Nie udało się usunąć danych połączenia');
      expect(cards(fixture)).toHaveLength(1);
    });
  });
});

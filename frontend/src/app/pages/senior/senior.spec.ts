import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { DemoService } from '../../api/api/demo.service';
import { SettingsService } from '../../api/api/settings.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Alert, Decision, Mode, SystemStatus, TranscriptSegment } from '../../api/model/models';
import { DecisionOutbox } from '../../core/decision-outbox';
import { ActiveCall, ConnectionState, EventsService } from '../../core/events.service';
import { SettingsStore } from '../../core/settings.store';
import { SPEECH_SYNTHESIS } from '../../core/voice.service';
import { Senior } from './senior';

const AT = '2026-10-04T10:00:00Z';
const CALL: ActiveCall = { callId: 'c1', startedAt: AT, endedAt: null, hadAlert: null };

function fakeEvents() {
  const connection = signal<ConnectionState>('open');
  return {
    connection,
    online: computed(() => connection() === 'open'),
    mode: signal<Mode | null>(Mode.SCRIPTED),
    systemStatus: signal<Partial<Record<string, SystemStatus>>>({}),
    activeCall: signal<ActiveCall | null>(null),
    segments: signal<TranscriptSegment[]>([]),
    alerts: signal<Alert[]>([]),
    decisions: signal<Decision[]>([]),
    phoneCall: signal<{ active: boolean; number: string } | null>(null),
    recording: signal(false),
  };
}

function alert(level: string, triggeredBy = 'both'): Alert {
  return {
    alertId: 'a1',
    callId: 'c1',
    level,
    stages: [
      { stage: 'SECRECY_DEMAND', segId: 's3', quote: 'proszę nikomu nie mówić', speakerRole: 'caller', source: 'llm', validated: true },
      { stage: 'MONEY_REQUEST', segId: 's9', quote: 'nie liczy się', speakerRole: 'caller', source: 'llm', validated: false },
    ],
    templateId: 't',
    shortText: 'Ta rozmowa może być oszustwem.',
    advice: 'Odczekaj minutę, zanim do kogoś zadzwonisz.',
    triggeredBy,
    createdAt: AT,
    mode: 'SCRIPTED',
  } as unknown as Alert;
}

const SEGMENT = {
  callId: 'c1', segId: 's3', tStartMs: 37_000, tEndMs: 40_000, text: 'Proszę nikomu nie mówić.', isFinal: true, speaker: 'A',
} as unknown as TranscriptSegment;

class FakeSynth {
  spoken: SpeechSynthesisUtterance[] = [];
  voices = [{ lang: 'en-US', name: 'en' }, { lang: 'pl-PL', name: 'Zosia' }] as SpeechSynthesisVoice[];
  getVoices = () => this.voices;
  speak = (u: SpeechSynthesisUtterance) => this.spoken.push(u);
  cancel = () => undefined;
  addEventListener = () => undefined;
}

describe('Senior', () => {
  let events: ReturnType<typeof fakeEvents>;
  let outbox: { send: ReturnType<typeof vi.fn>; unsaved: ReturnType<typeof signal<boolean>>; acknowledge: () => void };
  let settings: SettingsStore;
  let synth: FakeSynth | null;
  let demo: { setPhoneCall: ReturnType<typeof vi.fn> };
  let settingsApi: { getSettings: ReturnType<typeof vi.fn>; setSettings: ReturnType<typeof vi.fn> };

  const text = (f: ComponentFixture<Senior>) => (f.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';
  const $ = (f: ComponentFixture<Senior>, selector: string) => (f.nativeElement as HTMLElement).querySelector(selector);
  const button = (f: ComponentFixture<Senior>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent?.includes(label))!;

  function setup() {
    TestBed.configureTestingModule({
      imports: [Senior],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: DecisionOutbox, useValue: outbox },
        { provide: SPEECH_SYNTHESIS, useValue: synth },
        { provide: DemoService, useValue: demo },
        { provide: SettingsService, useValue: settingsApi },
      ],
    });
    settings = TestBed.inject(SettingsStore);
  }

  /** Renders the screen: protection is on from the start, nothing to tap. */
  async function started() {
    const fixture = TestBed.createComponent(Senior);
    await fixture.whenStable();
    return fixture;
  }

  async function withAlert(level = 'high', triggeredBy = 'both') {
    const fixture = await started();
    events.activeCall.set(CALL);
    events.segments.set([SEGMENT]);
    events.alerts.set([alert(level, triggeredBy)]);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    // jsdom has no speech synthesis; the fake below stands in for the browser's.
    vi.stubGlobal(
      'SpeechSynthesisUtterance',
      class {
        voice: SpeechSynthesisVoice | null = null;
        lang = '';
        constructor(readonly text: string) {}
      },
    );
    events = fakeEvents();
    const unsaved = signal(false);
    outbox = { send: vi.fn(), unsaved, acknowledge: () => unsaved.set(false) };
    synth = new FakeSynth();
    demo = { setPhoneCall: vi.fn(() => of({})) };
    settingsApi = {
      getSettings: vi.fn(() =>
        of({ seniorConsent: true, familyConsent: true, contacts: [], sensitivity: 'standard', retentionDays: 30, seniorName: '' }),
      ),
      setSettings: vi.fn(),
    };
  });

  afterEach(() => vi.unstubAllGlobals());

  describe('resting state', () => {
    beforeEach(setup);

    it('is protected from the start, without a button to switch protection on', async () => {
      const fixture = await started();
      expect($(fixture, 'app-status-panel')).not.toBeNull();
      expect(text(fixture)).toContain('Anioł Stróż słucha. Nic nie jest nagrywane.');
      expect(text(fixture)).not.toContain('Włącz ochronę');
    });

    it('unlocks speech with the first touch, once (WEB-07)', async () => {
      const fixture = await started();
      document.dispatchEvent(new Event('pointerdown'));
      document.dispatchEvent(new Event('pointerdown'));
      await fixture.whenStable();
      expect(synth!.spoken.length).toBe(1);
    });

    it('shows who is calling while the simulated phone call is on', async () => {
      const fixture = await started();
      expect($(fixture, '.phone-call')).toBeNull();

      events.phoneCall.set({ active: true, number: '+48 600 100 200' });
      await fixture.whenStable();
      expect(($(fixture, '.phone-call') as HTMLElement).textContent).toContain(
        'Trwa połączenie telefoniczne z numerem +48 600 100 200',
      );

      events.phoneCall.set(null);
      await fixture.whenStable();
      expect($(fixture, '.phone-call')).toBeNull();
    });

    it('says that recording is going on only while the listening device streams', async () => {
      const fixture = await started();
      expect($(fixture, '.recording')).toBeNull();

      events.recording.set(true);
      await fixture.whenStable();
      expect($(fixture, '.recording[role="status"]')?.textContent).toContain('Trwa nagrywanie rozmowy.');

      events.recording.set(false);
      await fixture.whenStable();
      expect($(fixture, '.recording')).toBeNull();
    });

    it('hides the transcript for the team until it is switched on', async () => {
      events.segments.set([SEGMENT]);
      const fixture = await started();
      expect($(fixture, 'app-transcript-debug-panel')).toBeNull();

      button(fixture, 'Dla zespołu').click();
      await fixture.whenStable();
      expect($(fixture, 'app-transcript-debug-panel')?.textContent).toContain('Proszę nikomu nie mówić.');

      button(fixture, 'Dla zespołu').click();
      await fixture.whenStable();
      expect($(fixture, 'app-transcript-debug-panel')).toBeNull();
    });

    it('shows offline in red when the socket is down', async () => {
      events.connection.set('closed');
      const fixture = await started();
      expect($(fixture, '.panel.red')).toBeTruthy();
      expect(text(fixture)).toContain('Anioł Stróż jest offline');
    });

    it('shows "Ochrona wstrzymana" in yellow when the Nasłuch device paused, with no pause button here (FF-13)', async () => {
      const fixture = await started();
      events.activeCall.set(CALL);
      await fixture.whenStable();
      expect($(fixture, '.panel.green')).toBeTruthy();
      expect(text(fixture)).not.toContain('Wstrzymaj');

      events.systemStatus.set({ audio: { component: 'audio', state: 'degraded', message: 'Ochrona wstrzymana przez użytkownika' } as SystemStatus });
      await fixture.whenStable();
      expect($(fixture, '.panel.yellow')).toBeTruthy();
      expect(text(fixture)).toContain('Ochrona wstrzymana');
      expect(text(fixture)).not.toContain('Wstrzymaj');
    });

    it('says when a decision could not be saved, until "Rozumiem" (FF-11)', async () => {
      const fixture = await started();
      expect(text(fixture)).not.toContain('Nie udało się zapisać decyzji.');

      outbox.unsaved.set(true);
      await fixture.whenStable();
      expect($(fixture, '.unsaved[role="alert"]')).toBeTruthy();
      expect(text(fixture)).toContain('Nie udało się zapisać decyzji.');

      button(fixture, 'Rozumiem').click();
      await fixture.whenStable();
      expect($(fixture, '.unsaved')).toBeNull();
    });

    it('a failure outranks a pause (FE-06)', async () => {
      const fixture = await started();
      events.activeCall.set(CALL);
      events.systemStatus.set({ audio: { component: 'audio', state: 'degraded', message: 'x' } as SystemStatus });
      events.connection.set('closed');
      await fixture.whenStable();
      expect($(fixture, '.panel.red')).toBeTruthy();
      expect(text(fixture)).toContain('Anioł Stróż jest offline');
    });
  });

  describe('AlertView', () => {
    beforeEach(setup);

    it('does not open for a low alert', async () => {
      const fixture = await withAlert('low');
      expect($(fixture, 'app-alert-view')).toBeNull();
    });

    it('shows the backend text, the source label and announces itself', async () => {
      const fixture = await withAlert('high', 'keywords');
      expect($(fixture, '[aria-live="assertive"]')).toBeTruthy();
      expect(text(fixture)).toContain('Ta rozmowa może być oszustwem.');
      expect(text(fixture)).toContain('wykryte po słowach kluczowych');
    });

    it('shows the advice on the alert screen before any decision (FF-12)', async () => {
      const fixture = await withAlert();
      expect($(fixture, 'app-alert-view .advice')?.textContent).toContain('Odczekaj minutę, zanim do kogoś zadzwonisz.');
    });

    it('has no "Dlaczego?", "Powtórz" or "To fałszywy alarm"', async () => {
      const fixture = await withAlert();
      expect(button(fixture, 'Dlaczego?')).toBeUndefined();
      expect(button(fixture, 'Powtórz')).toBeUndefined();
      expect(button(fixture, 'To fałszywy alarm')).toBeUndefined();
      expect($(fixture, 'app-evidence-quotes')).toBeNull();
    });

    it('"Rozłączam się" sends hung_up and shows the advice', async () => {
      const fixture = await withAlert();
      button(fixture, 'Rozłączam się').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'hung_up');
      expect(text(fixture)).toContain('Odczekaj minutę, zanim do kogoś zadzwonisz.');
      button(fixture, 'Gotowe').click();
      await fixture.whenStable();
      expect($(fixture, 'app-status-panel')).toBeTruthy();
    });

    it('"Rozłączam się" also ends the simulated phone call, so /listen stops listening', async () => {
      const fixture = await withAlert();
      expect(demo.setPhoneCall).not.toHaveBeenCalled();

      button(fixture, 'Rozłączam się').click();
      await fixture.whenStable();

      expect(demo.setPhoneCall).toHaveBeenCalledWith({ active: false });
    });

    it('"Zadzwoń do bliskiej osoby" also ends the simulated phone call', async () => {
      const fixture = await withAlert();
      button(fixture, 'Zadzwoń do bliskiej osoby').click();
      await fixture.whenStable();
      expect(demo.setPhoneCall).toHaveBeenCalledWith({ active: false });
    });

    it('"Zadzwoń na 112" ends the simulated phone call too, and does not stop the tel: link', async () => {
      const fixture = await withAlert();
      const link = $(fixture, 'a[href="tel:112"]') as HTMLAnchorElement;
      const click = new MouseEvent('click', { bubbles: true, cancelable: true });
      link.dispatchEvent(click);
      await fixture.whenStable();

      expect(demo.setPhoneCall).toHaveBeenCalledWith({ active: false });
      expect(click.defaultPrevented).toBe(false);
      expect(outbox.send).not.toHaveBeenCalled();
    });

    it('offers the first trusted contact of the settings as a tel: link (FE-10)', async () => {
      settingsApi.getSettings.mockReturnValue(
        of({ seniorConsent: true, familyConsent: true, contacts: [{ name: 'Ela', phone: '+48 602 000 222' }],
          sensitivity: 'standard', retentionDays: 30, seniorName: '' }),
      );
      const fixture = await withAlert();

      button(fixture, 'Zadzwoń do: Ela').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'called_trusted');
      expect(text(fixture)).toContain('+48 602 000 222');
      expect($(fixture, 'a[href^="tel:"]')?.getAttribute('href')).toBe('tel:+48 602 000 222');
    });

    it('"Zadzwoń na 112" is a tel: link right below "Zadzwoń do…", and dials nothing by itself (FE-09)', async () => {
      const fixture = await withAlert();
      const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('app-decision-buttons .decision')];
      expect(buttons.map((b) => b.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Rozłączam się',
        'Zadzwoń do bliskiej osoby',
        'Zadzwoń na 112',
      ]);
      expect(buttons[2].getAttribute('href')).toBe('tel:112');
      expect(outbox.send).not.toHaveBeenCalled();
    });

    it('without a saved contact says so instead of showing any number', async () => {
      const fixture = await withAlert();
      button(fixture, 'Zadzwoń do bliskiej osoby').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'called_trusted');
      expect(text(fixture)).toContain('Brak zapisanego numeru.');
      expect($(fixture, 'a[href^="tel:"]')).toBeNull();
    });

    it('closes when the call ends and shows "Rozmowa zakończona"', async () => {
      const fixture = await withAlert();
      events.activeCall.set({ ...CALL, endedAt: AT, hadAlert: true });
      await fixture.whenStable();

      expect($(fixture, 'app-alert-view')).toBeNull();
      expect(text(fixture)).toContain('Rozmowa zakończona');
    });

    it('closes on a senior decision that arrives from the backend', async () => {
      const fixture = await withAlert();
      events.decisions.set([{ alertId: 'a1', actor: 'senior', decision: 'hung_up', at: AT } as unknown as Decision]);
      await fixture.whenStable();
      expect($(fixture, 'app-status-panel')).toBeTruthy();
    });

    it('reads the alert aloud once with the Polish voice', async () => {
      const fixture = await withAlert();
      const said = synth!.spoken.filter((u) => u.text);
      // shortText, then advice, as one utterance (backend template, FF-12).
      expect(said.map((u) => u.text)).toEqual([
        'Ta rozmowa może być oszustwem. Odczekaj minutę, zanim do kogoś zadzwonisz.',
      ]);
      expect(said[0].voice?.lang).toBe('pl-PL');
    });
  });

  describe('backend failure during an alert (UC-01 test, bug 1)', () => {
    beforeEach(setup);

    it('keeps the warning on screen and says that Anioł Stróż is offline', async () => {
      const fixture = await withAlert();
      events.connection.set('closed');
      await fixture.whenStable();

      expect($(fixture, 'app-alert-view')).toBeTruthy();
      expect($(fixture, '.offline')?.textContent).toContain('Anioł Stróż jest offline');

      events.connection.set('open');
      await fixture.whenStable();
      expect($(fixture, '.offline')).toBeNull();
    });

    it('closes the alert and warns when the backend lost the call', async () => {
      const fixture = await withAlert();
      events.activeCall.set({ ...CALL, endedAt: AT, interrupted: true });
      await fixture.whenStable();

      expect($(fixture, 'app-alert-view')).toBeNull();
      expect(text(fixture)).toContain('Połączenie z Aniołem Stróżem zostało przerwane.');
      expect(text(fixture)).toContain('Ta rozmowa nie jest już sprawdzana.');
      expect(text(fixture)).not.toContain('Rozmowa zakończona');
    });
  });

  describe('without speech synthesis (WEB-09)', () => {
    beforeEach(() => {
      synth = null;
      setup();
    });

    it('shows the alert with an info line and keeps working', async () => {
      const fixture = await withAlert();
      expect(text(fixture)).toContain('Ta rozmowa może być oszustwem.');
      expect(text(fixture)).toContain('Brak głosu po polsku. Przeczytaj tekst na ekranie.');

      button(fixture, 'Rozłączam się').click();
      await fixture.whenStable();
      expect(outbox.send).toHaveBeenCalledWith('a1', 'hung_up');
    });
  });
});

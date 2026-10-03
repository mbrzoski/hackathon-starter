import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Alert, Decision, Mode, SystemStatus, TranscriptSegment } from '../../api/model/models';
import { AudioService } from '../../core/audio.service';
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
  let audio: { start: ReturnType<typeof vi.fn>; pause: ReturnType<typeof vi.fn>; resume: ReturnType<typeof vi.fn> };
  let outbox: { send: ReturnType<typeof vi.fn>; unsaved: ReturnType<typeof signal<boolean>>; acknowledge: () => void };
  let settings: SettingsStore;
  let synth: FakeSynth | null;

  const text = (f: ComponentFixture<Senior>) => (f.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';
  const $ = (f: ComponentFixture<Senior>, selector: string) => (f.nativeElement as HTMLElement).querySelector(selector);
  const button = (f: ComponentFixture<Senior>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent?.includes(label))!;

  function setup() {
    TestBed.configureTestingModule({
      imports: [Senior],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: AudioService, useValue: audio },
        { provide: DecisionOutbox, useValue: outbox },
        { provide: SPEECH_SYNTHESIS, useValue: synth },
      ],
    });
    settings = TestBed.inject(SettingsStore);
  }

  /** Renders the screen and taps "Włącz ochronę". */
  async function started() {
    const fixture = TestBed.createComponent(Senior);
    await fixture.whenStable();
    button(fixture, 'Włącz ochronę').click();
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
    audio = { start: vi.fn(), pause: vi.fn(), resume: vi.fn() };
    const unsaved = signal(false);
    outbox = { send: vi.fn(), unsaved, acknowledge: () => unsaved.set(false) };
    synth = new FakeSynth();
  });

  afterEach(() => vi.unstubAllGlobals());

  describe('resting state', () => {
    beforeEach(setup);

    it('starts with "Włącz ochronę", which unlocks speech and starts audio (WEB-07)', async () => {
      const fixture = TestBed.createComponent(Senior);
      await fixture.whenStable();
      expect($(fixture, 'app-status-panel')).toBeNull();

      button(fixture, 'Włącz ochronę').click();
      await fixture.whenStable();

      expect(audio.start).toHaveBeenCalledTimes(1);
      expect(synth!.spoken.length).toBe(1);
      expect(text(fixture)).toContain('Anioł Stróż słucha. Nic nie jest nagrywane.');
    });

    it('shows offline on the start screen too (FE-06)', async () => {
      events.connection.set('closed');
      const fixture = TestBed.createComponent(Senior);
      await fixture.whenStable();
      expect($(fixture, '.start-status.red')?.textContent).toContain('Anioł Stróż jest offline');
    });

    it('shows offline in red when the socket is down', async () => {
      events.connection.set('closed');
      const fixture = await started();
      expect($(fixture, '.panel.red')).toBeTruthy();
      expect(text(fixture)).toContain('Anioł Stróż jest offline');
    });

    it('pauses and resumes protection for the current call only', async () => {
      const fixture = await started();
      events.activeCall.set(CALL);
      await fixture.whenStable();

      button(fixture, 'Wstrzymaj dla tej rozmowy').click();
      await fixture.whenStable();
      expect(audio.pause).toHaveBeenCalledTimes(1);

      button(fixture, 'Wznów ochronę').click();
      await fixture.whenStable();
      expect(audio.resume).toHaveBeenCalledTimes(1);

      button(fixture, 'Wstrzymaj dla tej rozmowy').click();
      events.activeCall.set({ ...CALL, callId: 'c2' });
      await fixture.whenStable();
      expect(button(fixture, 'Wstrzymaj dla tej rozmowy')).toBeTruthy();
    });

    it('a local pause shows "Ochrona wstrzymana" in yellow, not the green state (FF-13)', async () => {
      const fixture = await started();
      events.activeCall.set(CALL);
      await fixture.whenStable();
      expect($(fixture, '.panel.green')).toBeTruthy();

      button(fixture, 'Wstrzymaj dla tej rozmowy').click();
      await fixture.whenStable();
      expect($(fixture, '.panel.yellow')).toBeTruthy();
      expect(text(fixture)).toContain('Ochrona wstrzymana');
      expect(text(fixture)).not.toContain('Anioł Stróż słucha.');

      button(fixture, 'Wznów ochronę').click();
      await fixture.whenStable();
      expect($(fixture, '.panel.green')).toBeTruthy();
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

    it('a failure still outranks the local pause (FE-06)', async () => {
      const fixture = await started();
      events.activeCall.set(CALL);
      await fixture.whenStable();
      button(fixture, 'Wstrzymaj dla tej rozmowy').click();
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

    it('"Dlaczego?" shows the stage name, the quote and the time from the start of the call', async () => {
      const fixture = await withAlert('medium');
      expect($(fixture, 'app-evidence-quotes')).toBeNull();

      button(fixture, 'Dlaczego?').click();
      await fixture.whenStable();

      expect(text(fixture)).toContain('Prośba o tajemnicę');
      expect($(fixture, 'q')?.textContent).toBe('proszę nikomu nie mówić');
      expect(text(fixture)).toContain('00:37 od początku rozmowy');
      expect(text(fixture)).not.toContain('nie liczy się');
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

    it('"Zadzwoń do…" sends called_trusted and shows the number from settings as a tel: link (FE-09, FE-10)', async () => {
      settings.firstContact.set({ name: 'Ania', phone: '+48 602 000 222' });
      const fixture = await withAlert();
      button(fixture, 'Zadzwoń do: Ania').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'called_trusted');
      expect(text(fixture)).toContain('+48 602 000 222');
      expect($(fixture, 'a[href^="tel:"]')?.getAttribute('href')).toBe('tel:+48 602 000 222');
    });

    it('without a saved contact says so instead of showing any number', async () => {
      const fixture = await withAlert();
      button(fixture, 'Zadzwoń do bliskiej osoby').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'called_trusted');
      expect(text(fixture)).toContain('Brak zapisanego numeru.');
      expect($(fixture, 'a[href^="tel:"]')).toBeNull();
    });

    it('"To fałszywy alarm" sends false_alarm and returns to StatusPanel', async () => {
      const fixture = await withAlert();
      button(fixture, 'To fałszywy alarm').click();
      await fixture.whenStable();

      expect(outbox.send).toHaveBeenCalledWith('a1', 'false_alarm');
      expect($(fixture, 'app-status-panel')).toBeTruthy();
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

    it('reads the alert aloud once with the Polish voice and again on "Powtórz"', async () => {
      const fixture = await withAlert();
      const said = synth!.spoken.filter((u) => u.text);
      // shortText, then advice, as one utterance (backend template, FF-12).
      expect(said.map((u) => u.text)).toEqual([
        'Ta rozmowa może być oszustwem. Odczekaj minutę, zanim do kogoś zadzwonisz.',
      ]);
      expect(said[0].voice?.lang).toBe('pl-PL');

      button(fixture, 'Powtórz').click();
      expect(synth!.spoken.filter((u) => u.text).length).toBe(2);
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
      expect(button(fixture, 'Powtórz')).toBeUndefined();

      button(fixture, 'To fałszywy alarm').click();
      await fixture.whenStable();
      expect(outbox.send).toHaveBeenCalledWith('a1', 'false_alarm');
    });
  });
});

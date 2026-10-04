import { Component, computed, effect, inject, linkedSignal, signal, untracked } from '@angular/core';
import { Alert, DecisionRequestDecisionEnum, RiskLevel } from '../../api/model/models';
import { DemoService } from '../../api/api/demo.service';
import { DecisionOutbox } from '../../core/decision-outbox';
import { EventsService } from '../../core/events.service';
import { selectSeniorStatus } from '../../core/senior-status';
import { SettingsStore } from '../../core/settings.store';
import { VoiceService } from '../../core/voice.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { problemDetailText } from '../../shared/problem-detail';
import { injectNow } from '../../shared/time';
import { AlertView } from './alert-view';
import { DecisionOutcome } from './decision-outcome';
import { TranscriptDebugPanel, keywordHighlights } from '../../shared/transcript-debug-panel';
import { StatusPanel } from './status-panel';

const ALERTING_LEVELS: ReadonlySet<RiskLevel> = new Set([RiskLevel.medium, RiskLevel.high]);
const ENDED_NOTICE_MS = 6000;
/** Longer: the senior has to understand that this call is no longer being checked. */
const INTERRUPTED_NOTICE_MS = 15_000;

type View =
  | { kind: 'status' }
  | { kind: 'alert'; alert: Alert }
  | { kind: 'outcome'; alert: Alert; decision: 'hung_up' | 'called_trusted' }
  | { kind: 'ended'; interrupted: boolean };

/**
 * Senior screen: full screen in portrait and landscape, never scrolls, text >= 28 px (WEB-11).
 * Resting state from selectSeniorStatus (FE-06). A medium or high alert shows AlertView until the senior decides
 * or the call ends (then "Rozmowa zakończona" for a few seconds, or a warning if the backend lost the call).
 */
@Component({
  selector: 'app-senior',
  imports: [AlertView, DecisionOutcome, Icon, ModeBadge, StatusPanel, TranscriptDebugPanel],
  template: `
    <div class="phone"><div class="screen">
    <header>
      <div class="brand"><span class="logo"><app-icon name="shield" [size]="32" /></span><span class="name">Anioł Stróż</span></div>
      <app-mode-badge [large]="true" [compact]="true" />
    </header>
    @let v = view();
    @if (events.phoneCall(); as call) {
      <!-- Demo: the family panel simulates an incoming call; nothing is dialled. -->
      <p class="phone-call" role="status">
        <app-icon name="phone" [size]="36" /> Trwa połączenie telefoniczne z numerem {{ call.number }}
      </p>
    }
    @if (events.recording()) {
      <!-- The listening device streams the call to the backend. The sound itself is not stored (rule 4). -->
      <p class="recording" role="status">
        <app-icon name="mic" [size]="36" /> Trwa nagrywanie rozmowy. Dźwięk nie jest zapisywany.
      </p>
    }
    @if (!events.online() && (v.kind === 'alert' || v.kind === 'outcome' || v.kind === 'ended')) {
      <!-- FE-06: the warning stays on screen, but the senior must know that nothing new can arrive. -->
      <p class="offline" role="alert"><app-icon name="cloud-off" [size]="32" /> Anioł Stróż jest offline</p>
    }
    <main>
      @if (v.kind === 'alert') {
        <app-alert-view
          [alert]="v.alert"
          [contactName]="settings.firstContact()?.name || null"
          (emergency)="endPhoneCall()"
          (decide)="decide(v.alert, $event)" />
      } @else if (v.kind === 'outcome') {
        <app-decision-outcome
          [kind]="v.decision"
          [alert]="v.alert"
          [contact]="settings.firstContact()"
          (done)="close(v.alert.alertId)" />
      } @else if (v.kind === 'ended') {
        @if (v.interrupted) {
          <section class="notice interrupted" role="alert">
            <app-icon name="warning" [size]="96" />
            <p>Połączenie z Aniołem Stróżem zostało przerwane.</p>
            <p class="sub">Ta rozmowa nie jest już sprawdzana. Jeśli coś Cię niepokoi, rozłącz się.</p>
          </section>
        } @else {
          <section class="notice" role="status" aria-live="polite">
            <app-icon name="check" [size]="96" />
            <p>Rozmowa zakończona</p>
          </section>
        }
      } @else {
        @if (outbox.unsaved()) {
          <!-- FF-11: a decision that did not reach the backend is never silent. -->
          <section class="unsaved" role="alert">
            <p><app-icon name="warning" [size]="36" /> Nie udało się zapisać decyzji. Powiedz o niej bliskiej osobie.</p>
            <button type="button" (click)="outbox.acknowledge()">Rozumiem</button>
          </section>
        }
        <app-status-panel [status]="status()" />
      }
    </main>
    <button type="button" class="team-toggle" [attr.aria-pressed]="showTranscript()" (click)="showTranscript.set(!showTranscript())">
      Dla zespołu
    </button>
    @if (showTranscript()) {
      <!-- Team only: an overlay, so the senior screen itself never scrolls (WEB-11). -->
      <div class="team-panel">
        <app-transcript-debug-panel [segments]="events.segments()" [highlights]="highlights()" />
      </div>
    }
    </div></div>
  `,
  styleUrl: './senior.scss',
  host: { '(document:pointerdown)': 'unlockVoice()' },
})
export class Senior {
  protected readonly events = inject(EventsService);
  protected readonly settings = inject(SettingsStore);
  private readonly voice = inject(VoiceService);
  private readonly demo = inject(DemoService);
  protected readonly outbox = inject(DecisionOutbox);
  private readonly now = injectNow();

  /** Speech is allowed by the browser after the first touch anywhere on the screen (WEB-07). */
  private voiceUnlocked = false;
  /** Transcript for the team, hidden unless switched on. */
  protected readonly showTranscript = signal(false);
  protected readonly highlights = computed(() => keywordHighlights(this.events.alerts()));
  /** Decisions taken on this screen; they apply at once, sending happens in the background. */
  private readonly decided = signal<ReadonlyMap<string, DecisionRequestDecisionEnum>>(new Map());
  /** Alerts the senior has finished with (false alarm, or "Gotowe" after a decision). */
  private readonly closed = signal<ReadonlySet<string>>(new Set());

  protected readonly status = computed(() =>
    selectSeniorStatus(this.events.connection(), this.events.systemStatus(), this.events.activeCall()),
  );

  protected readonly callInProgress = computed(() => {
    const call = this.events.activeCall();
    return !!call && !call.endedAt;
  });

  /** Medium or high alerts of the current call that the senior has not finished with, newest first. */
  private readonly openAlerts = computed(() => {
    const closed = this.closed();
    const decided = this.decided();
    // A senior decision from elsewhere (another device, earlier session) also closes the alert.
    const decidedRemotely = new Set(
      this.events
        .decisions()
        .filter((d) => d.actor === 'senior')
        .map((d) => d.alertId),
    );
    return [...this.events.alerts()]
      .reverse()
      .filter((a) => ALERTING_LEVELS.has(a.level) && !closed.has(a.alertId))
      .filter((a) => decided.has(a.alertId) || !decidedRemotely.has(a.alertId));
  });

  /** Local time at which this screen saw the current call end. */
  private readonly endedSeenAt = linkedSignal({
    source: () => this.events.activeCall()?.endedAt ?? null,
    computation: (endedAt) => (endedAt ? Date.now() : null),
  });

  protected readonly view = computed<View>(() => {
    const alert = this.openAlerts()[0];
    if (alert) {
      if (!this.callInProgress()) {
        const seen = this.endedSeenAt();
        const interrupted = !!this.events.activeCall()?.interrupted;
        const showFor = interrupted ? INTERRUPTED_NOTICE_MS : ENDED_NOTICE_MS;
        return seen !== null && this.now() - seen < showFor ? { kind: 'ended', interrupted } : this.restingView();
      }
      const decision = this.decided().get(alert.alertId);
      if (decision === DecisionRequestDecisionEnum.hung_up || decision === DecisionRequestDecisionEnum.called_trusted) {
        return { kind: 'outcome', alert, decision };
      }
      return { kind: 'alert', alert };
    }
    return this.restingView();
  });

  /** Protection is always on (the Nasłuch device listens), so the first touch only unlocks the voice (WEB-07). */
  protected unlockVoice(): void {
    if (!this.voiceUnlocked) {
      this.voiceUnlocked = true;
      this.voice.unlock();
    }
  }

  /**
   * The senior hung up, or went on to call a family member or 112: the simulated phone call ends everywhere, so
   * /listen stops listening until the family panel starts a new one. Nothing is dialled or cut by the app itself
   * (FE-09): the number opens only as a link the senior taps.
   */
  protected endPhoneCall(): void {
    this.demo.setPhoneCall({ active: false }).subscribe({
      error: (err: unknown) => console.warn(`Ending the simulated phone call failed: ${problemDetailText(err)}`),
    });
  }

  protected decide(alert: Alert, decision: DecisionRequestDecisionEnum): void {
    this.decided.update((map) => new Map(map).set(alert.alertId, decision));
    if (decision === DecisionRequestDecisionEnum.false_alarm) {
      this.close(alert.alertId);
    }
    this.outbox.send(alert.alertId, decision);
    if (decision === DecisionRequestDecisionEnum.hung_up || decision === DecisionRequestDecisionEnum.called_trusted) {
      this.endPhoneCall();
    }
  }

  protected close(alertId: string): void {
    this.closed.update((set) => new Set(set).add(alertId));
  }

  constructor() {
    this.settings.load();
    // The family may change the number any time: read it again when an alert appears, before the senior can tap it.
    effect(() => {
      if (this.openAlerts()[0]) {
        untracked(() => this.settings.load());
      }
    });
  }

  private restingView(): View {
    return { kind: 'status' };
  }
}


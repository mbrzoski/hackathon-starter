import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { Alert, DecisionRequestDecisionEnum, RiskLevel } from '../../api/model/models';
import { DecisionOutbox } from '../../core/decision-outbox';
import { EventsService } from '../../core/events.service';
import { selectSeniorStatus } from '../../core/senior-status';
import { SettingsStore } from '../../core/settings.store';
import { VoiceService } from '../../core/voice.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { injectNow } from '../../shared/time';
import { AlertView } from './alert-view';
import { DecisionOutcome } from './decision-outcome';
import { STATUS_VIEW } from '../../shared/status-view';
import { TranscriptDebugPanel, keywordHighlights } from '../../shared/transcript-debug-panel';
import { StatusPanel } from './status-panel';

const ALERTING_LEVELS: ReadonlySet<RiskLevel> = new Set([RiskLevel.medium, RiskLevel.high]);
const ENDED_NOTICE_MS = 6000;
/** Longer: the senior has to understand that this call is no longer being checked. */
const INTERRUPTED_NOTICE_MS = 15_000;

type View =
  | { kind: 'start' }
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
    <header>
      <div class="brand"><span class="logo"><app-icon name="shield" [size]="32" /></span><span class="name">Anioł Stróż</span></div>
      <app-mode-badge [large]="true" [compact]="true" />
    </header>
    @let v = view();
    @if (!events.online() && (v.kind === 'alert' || v.kind === 'outcome' || v.kind === 'ended')) {
      <!-- FE-06: the warning stays on screen, but the senior must know that nothing new can arrive. -->
      <p class="offline" role="alert"><app-icon name="cloud-off" [size]="32" /> Anioł Stróż jest offline</p>
    }
    <main>
      @if (v.kind === 'alert') {
        <app-alert-view
          [alert]="v.alert"
          [segments]="events.segments()"
          [contactName]="settings.firstContact()?.name ?? null"
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
      } @else if (v.kind === 'start') {
        <section class="start">
          <app-icon name="shield" [size]="96" />
          <p>Dotknij, aby włączyć ochronę i głos.</p>
          @if (status() !== 'protected') {
            <!-- FE-06: a failure is visible before the senior starts, too. -->
            <p class="start-status" [class]="startStatus().tone" role="status">
              <app-icon [name]="startStatus().icon" [size]="36" /> {{ startStatus().text }}
            </p>
          }
          <!-- WEB-07: the tap unlocks speech now and will start the microphone. -->
          <button type="button" class="decision" (click)="start()">Włącz ochronę</button>
        </section>
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
  `,
  styleUrl: './senior.scss',
})
export class Senior {
  protected readonly events = inject(EventsService);
  protected readonly settings = inject(SettingsStore);
  private readonly voice = inject(VoiceService);
  protected readonly outbox = inject(DecisionOutbox);
  private readonly now = injectNow();

  private readonly started = signal(false);
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

  protected readonly startStatus = computed(() => STATUS_VIEW[this.status()]);

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

  protected start(): void {
    // The microphone belongs to the Nasłuch device (/listen), not to this phone (use-cases.md, step 2).
    this.voice.unlock();
    this.started.set(true);
  }

  protected decide(alert: Alert, decision: DecisionRequestDecisionEnum): void {
    this.decided.update((map) => new Map(map).set(alert.alertId, decision));
    if (decision === DecisionRequestDecisionEnum.false_alarm) {
      this.close(alert.alertId);
    }
    this.outbox.send(alert.alertId, decision);
  }

  protected close(alertId: string): void {
    this.closed.update((set) => new Set(set).add(alertId));
  }

  private restingView(): View {
    return this.started() ? { kind: 'status' } : { kind: 'start' };
  }
}


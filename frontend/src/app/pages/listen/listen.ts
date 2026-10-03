import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { AudioService } from '../../core/audio.service';
import { DecisionActorEnum, DecisionDecisionEnum, SystemStatusStateEnum } from '../../api/model/models';
import { openAlert } from '../../core/call-view';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { RISK_LEVEL_WORDS } from '../../shared/risk-level-chip';
import { formatDuration, injectNow } from '../../shared/time';

type ListenView = 'offline' | 'ended' | 'starting' | 'blocked' | 'audio_lost' | 'alarm' | 'listening' | 'waiting';

/** How often a microphone that is not running is tried again. */
const RETRY_MS = 5000;

/** After the senior hung up, how long the screen says so before it listens for the next call. */
const NEXT_CALL_MS = 3000;

/**
 * "Nasłuch": navy full-screen front for the tablet next to the landline. Connects to /ws/events as the senior.
 * Protection is always on: the screen starts the microphone by itself and tries again after a failure, so nobody has
 * to tap anything. The button only appears when the browser blocks the automatic start.
 */
@Component({
  selector: 'app-listen',
  imports: [Icon, ModeBadge],
  templateUrl: './listen.html',
  styleUrl: './listen.scss',
})
export class Listen {
  protected readonly events = inject(EventsService);
  protected readonly audio = inject(AudioService);
  private readonly now = injectNow();
  /** The senior chose "Rozłączam się": this call is over, the microphone starts again for the next one. */
  protected readonly hungUp = signal(false);
  /** The call already ended because of a hang-up, so a late event cannot end the next call. */
  private endedCallId: string | null = null;
  protected readonly bars = [14, 26, 40, 22, 52, 64, 34, 18, 30, 56, 64, 38, 20, 14, 30, 48, 60, 36, 20, 28];

  protected readonly alert = computed(() =>
    openAlert(this.events.alerts(), this.events.decisions(), DecisionActorEnum.senior),
  );

  protected readonly view = computed<ListenView>(() => {
    const audio = this.events.systemStatus().audio;
    const call = this.events.activeCall();
    if (!this.events.online()) return 'offline';
    if (this.hungUp()) return 'ended';
    const microphone = this.audio.state();
    if (microphone === 'idle' || microphone === 'requesting') return 'starting';
    if (microphone !== 'listening') return 'blocked';
    if (audio && audio.state !== SystemStatusStateEnum.ok) return 'audio_lost';
    // Only an ongoing call can be alarming: after call.ended (or a backend restart) the tablet waits again.
    if (call && !call.endedAt) return this.alert() ? 'alarm' : 'listening';
    return 'waiting';
  });

  protected readonly callTime = computed(() => {
    const call = this.events.activeCall();
    return call ? formatDuration((this.now() - Date.parse(call.startedAt)) / 1000) : '00:00';
  });

  protected readonly firstQuote = computed(() => this.alert()?.stages.find((hit) => hit.validated)?.quote ?? null);
  protected readonly riskWord = computed(() => {
    const level = this.events.risk()?.level ?? this.alert()?.level;
    return level ? RISK_LEVEL_WORDS[level] : null;
  });


  /** The tap is the fallback when the browser did not allow the automatic start (WEB-07). */
  protected start(): void {
    void this.audio.start();
  }

  constructor() {
    // The senior hung up: the call is over, so stop sending its audio. The backend ends the call (call.ended for
    // everyone); the screen never touches the phone line itself (FE-09).
    effect(() => {
      const call = this.events.activeCall();
      if (!call || call.endedAt || call.callId === this.endedCallId) {
        return;
      }
      const alertIds = new Set(this.events.alerts().map((alert) => alert.alertId));
      const hungUp = this.events
        .decisions()
        .some((d) => d.actor === DecisionActorEnum.senior && d.decision === DecisionDecisionEnum.hung_up && alertIds.has(d.alertId));
      if (hungUp) {
        this.endedCallId = call.callId;
        untracked(() => {
          this.hungUp.set(true);
          this.audio.stop();
        });
      }
    });

    effect((onCleanup) => {
      if (!this.events.online()) {
        return;
      }
      const state = this.audio.state();
      if (this.hungUp()) {
        // Protection stays on: after a short "Rozmowa zakończona" the microphone starts again for the next call.
        const timer = setTimeout(() => this.hungUp.set(false), NEXT_CALL_MS);
        onCleanup(() => clearTimeout(timer));
      } else if (state === 'idle') {
        untracked(() => void this.audio.start());
      } else if (state === 'error') {
        // Never silent (the screen says why), never given up: a cable, a cut connection or a late consent heals itself.
        const timer = setTimeout(() => void this.audio.start(), RETRY_MS);
        onCleanup(() => clearTimeout(timer));
      }
    });
  }
}

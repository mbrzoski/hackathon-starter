import { Component, computed, effect, inject, untracked } from '@angular/core';
import { AudioService } from '../../core/audio.service';
import { DecisionActorEnum, SystemStatusStateEnum } from '../../api/model/models';
import { openAlert } from '../../core/call-view';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { RISK_LEVEL_WORDS } from '../../shared/risk-level-chip';
import { formatDuration, injectNow } from '../../shared/time';

type ListenView = 'offline' | 'starting' | 'blocked' | 'audio_lost' | 'alarm' | 'listening' | 'waiting';

/** How often a microphone that is not running is tried again. */
const RETRY_MS = 5000;

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
  protected readonly bars = [14, 26, 40, 22, 52, 64, 34, 18, 30, 56, 64, 38, 20, 14, 30, 48, 60, 36, 20, 28];

  protected readonly alert = computed(() =>
    openAlert(this.events.alerts(), this.events.decisions(), DecisionActorEnum.senior),
  );

  protected readonly view = computed<ListenView>(() => {
    const audio = this.events.systemStatus().audio;
    const call = this.events.activeCall();
    if (!this.events.online()) return 'offline';
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
    effect((onCleanup) => {
      if (!this.events.online()) {
        return;
      }
      const state = this.audio.state();
      if (state === 'idle') {
        untracked(() => void this.audio.start());
      } else if (state === 'error') {
        // Never silent (the screen says why), never given up: a cable, a cut connection or a late consent heals itself.
        const timer = setTimeout(() => void this.audio.start(), RETRY_MS);
        onCleanup(() => clearTimeout(timer));
      }
    });
  }
}

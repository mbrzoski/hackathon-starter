import { Component, computed, inject, signal } from '@angular/core';
import { DecisionActorEnum, SystemStatusStateEnum } from '../../api/model/models';
import { openAlert } from '../../core/call-view';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { RISK_LEVEL_WORDS } from '../../shared/risk-level-chip';
import { formatDuration, injectNow } from '../../shared/time';

type ListenView = 'offline' | 'idle' | 'audio_lost' | 'alarm' | 'listening' | 'waiting';

/** "Nasłuch": navy full-screen front for the tablet next to the landline. Connects to /ws/events as the senior. */
@Component({
  selector: 'app-listen',
  imports: [Icon, ModeBadge],
  templateUrl: './listen.html',
  styleUrl: './listen.scss',
})
export class Listen {
  protected readonly events = inject(EventsService);
  private readonly now = injectNow();
  private readonly armed = signal(false);
  protected readonly bars = [14, 26, 40, 22, 52, 64, 34, 18, 30, 56, 64, 38, 20, 14, 30, 48, 60, 36, 20, 28];

  protected readonly alert = computed(() =>
    openAlert(this.events.alerts(), this.events.decisions(), DecisionActorEnum.senior),
  );

  protected readonly view = computed<ListenView>(() => {
    const audio = this.events.systemStatus().audio;
    const call = this.events.activeCall();
    if (!this.events.online()) return 'offline';
    if (!this.armed()) return 'idle';
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

  protected start(): void {
    // WEB-07: must run from a user gesture (this click).
    // TODO: request the microphone here (getUserMedia + AudioWorklet to /ws/audio, WEB-08) in the audio task.
    this.armed.set(true);
  }
}

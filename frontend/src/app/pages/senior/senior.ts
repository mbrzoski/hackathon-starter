import { Component, computed, inject, signal } from '@angular/core';
import { DecisionActorEnum, SystemStatusStateEnum } from '../../api/model/models';
import { HIT_SOURCE_LABEL, openAlert, segmentStartSeconds } from '../../core/call-view';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { StageLabelPipe } from '../../shared/stage-label.pipe';
import { formatDuration } from '../../shared/time';

/** FE-06 priority: offline > alert > can't hear > paused > basic protection > protected. Never green on a failure. */
type SeniorView = 'offline' | 'alert' | 'audio_lost' | 'paused' | 'basic' | 'protected';

/**
 * Senior app (later wrapped in Capacitor). Text >= 28 px, buttons >= 64 px (decisions 72 px), WEB-11.
 * Alert texts come from the backend template (FE-04). Numbers only from settings (FE-10); tel: only on tap (FE-09).
 */
@Component({
  selector: 'app-senior',
  imports: [Icon, ModeBadge, StageLabelPipe],
  templateUrl: './senior.html',
  styleUrl: './senior.scss',
})
export class Senior {
  protected readonly events = inject(EventsService);
  /** Alerts the senior marked as false alarm on this device, until the decision endpoint exists. */
  private readonly dismissed = signal<ReadonlySet<string>>(new Set());

  protected readonly alert = computed(() => {
    const alert = openAlert(this.events.alerts(), this.events.decisions(), DecisionActorEnum.senior);
    return alert && !this.dismissed().has(alert.alertId) ? alert : null;
  });

  protected readonly view = computed<SeniorView>(() => {
    const status = this.events.systemStatus();
    if (!this.events.online() || status.backend?.state === SystemStatusStateEnum.down) return 'offline';
    if (this.alert()) return 'alert';
    if (status.audio && status.audio.state !== SystemStatusStateEnum.ok) return 'audio_lost';
    if (status.stt && status.stt.state !== SystemStatusStateEnum.ok) return 'paused';
    if (status.ai && status.ai.state !== SystemStatusStateEnum.ok) return 'basic';
    return 'protected';
  });

  /** Evidence behind "Dlaczego?" (FE-08): stage, verbatim quote, time, source. */
  protected readonly evidence = computed(() =>
    (this.alert()?.stages ?? [])
      .filter((hit) => hit.validated)
      .map((hit) => {
        const start = segmentStartSeconds(this.events.segments(), hit.segId);
        return { hit, time: start === null ? null : formatDuration(start), source: HIT_SOURCE_LABEL[hit.source] };
      }),
  );

  // TODO: the trusted contact and its number come from GET /api/settings once the contract has paths (FE-10).
  protected readonly contact = signal<{ name: string; phone: string } | null>(null);
  protected readonly emergencyNumber = '112';

  protected falseAlarm(alertId: string): void {
    // TODO: POST /api/alerts/{id}/decision (false_alarm) through the generated service once the contract has paths.
    this.dismissed.update((set) => new Set(set).add(alertId));
  }
}

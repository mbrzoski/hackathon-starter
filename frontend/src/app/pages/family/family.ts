import { Component, computed, inject } from '@angular/core';
import { DecisionActorEnum, StageId } from '../../api/model/models';
import {
  HIT_SOURCE_LABEL,
  TRIGGERED_BY_LABEL,
  evidenceByStage,
  openAlert,
  segmentStartSeconds,
} from '../../core/call-view';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { normalize } from '../../shared/normalize';
import { RiskLevelChip } from '../../shared/risk-level-chip';
import { StageLabelPipe } from '../../shared/stage-label.pipe';
import { formatClock, formatDuration, injectNow } from '../../shared/time';

interface TranscriptRow {
  segId: string;
  time: string;
  speaker: string;
  isFinal: boolean;
  before: string;
  quote: string;
  after: string;
  stage: StageId | null;
}

const SPEAKER_LABEL = { A: 'Mówca A', B: 'Mówca B', unknown: 'Nieznany mówca' } as const;

/** Family panel. Mobile first, two columns from 900 px (WEB-12). Level and stages come from the backend (FE-04). */
@Component({
  selector: 'app-family',
  imports: [Icon, ModeBadge, RiskLevelChip, StageLabelPipe],
  templateUrl: './family.html',
  styleUrl: './family.scss',
})
export class Family {
  protected readonly events = inject(EventsService);
  private readonly now = injectNow();

  protected readonly alert = computed(() =>
    openAlert(this.events.alerts(), this.events.decisions(), DecisionActorEnum.family),
  );
  protected readonly alertTime = computed(() => {
    const a = this.alert();
    return a ? formatClock(a.createdAt) : '';
  });
  protected readonly triggeredBy = computed(() => {
    const a = this.alert();
    return a ? TRIGGERED_BY_LABEL[a.triggeredBy] : '';
  });

  protected readonly callStart = computed(() => {
    const call = this.events.activeCall();
    return call ? formatClock(call.startedAt) : '';
  });
  protected readonly callDuration = computed(() => {
    const call = this.events.activeCall();
    if (!call) return '';
    const end = call.endedAt ? Date.parse(call.endedAt) : this.now();
    return formatDuration((end - Date.parse(call.startedAt)) / 1000);
  });

  private readonly evidence = computed(() => evidenceByStage(this.events.alerts()));

  /** Transcript with each validated quote highlighted where it was said (CON-05 normalisation as fallback). */
  protected readonly rows = computed<TranscriptRow[]>(() => {
    const hits = [...this.evidence().values()];
    return this.events.segments().map((seg) => {
      const hit = hits.find((h) => h.segId === seg.segId);
      const row: TranscriptRow = {
        segId: seg.segId,
        time: formatDuration(seg.tStartMs / 1000),
        speaker: SPEAKER_LABEL[seg.speaker],
        isFinal: seg.isFinal,
        before: seg.text,
        quote: '',
        after: '',
        stage: hit?.stage ?? null,
      };
      if (!hit) return row;
      const at = seg.text.indexOf(hit.quote);
      if (at >= 0) {
        return { ...row, before: seg.text.slice(0, at), quote: hit.quote, after: seg.text.slice(at + hit.quote.length) };
      }
      // Quote matched only after normalisation: highlight the whole segment.
      return normalize(seg.text).includes(normalize(hit.quote)) ? { ...row, before: '', quote: seg.text } : row;
    });
  });

  /** Stages counted by the backend, each with its evidence (FE-08). */
  protected readonly stages = computed(() =>
    // Typed as Set (uniqueItems) by the generator, but JSON delivers an array: spread handles both.
    [...(this.events.risk()?.stages ?? [])].map((stage) => {
      const hit = this.evidence().get(stage);
      const start = hit ? segmentStartSeconds(this.events.segments(), hit.segId) : null;
      return {
        stage,
        quote: hit?.quote ?? null,
        time: start === null ? null : formatDuration(start),
        source: hit ? HIT_SOURCE_LABEL[hit.source] : null,
      };
    }),
  );
}

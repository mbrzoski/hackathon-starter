import { Component, computed, input, output } from '@angular/core';
import { Alert, StageId, TranscriptSegment } from '../../api/model/models';
import { HIT_SOURCE_LABEL } from '../../core/call-view';
import { StageLabelPipe } from '../../shared/stage-label.pipe';
import { formatDuration } from '../../shared/time';
import { stagesInOrder } from './evidence';

/** Stages in the order they were said: Polish name, verbatim quote, time, source (FE-08), "nie licz" toggle. */
@Component({
  selector: 'app-stage-timeline',
  imports: [StageLabelPipe],
  template: `
    <ol>
      @for (s of stages(); track s.stage; let i = $index) {
        <li [class.ignored]="ignored().has(s.stage)">
          <span class="num">{{ i + 1 }}</span>
          <div class="body">
            <strong>{{ s.stage | stageLabel }}</strong>
            <q>{{ s.quote }}</q>
            <span class="meta">{{ s.time ? s.time + ' · ' : '' }}{{ s.source }}</span>
            @if (canIgnore()) {
              <label class="toggle">
                <input type="checkbox" [checked]="ignored().has(s.stage)" (change)="toggle.emit(s.stage)" />
                nie licz tego etapu
              </label>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    ol { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
    li { display: flex; gap: 12px; }
    .body { display: grid; gap: 2px; min-width: 0; }
    .num { flex: none; width: 28px; height: 28px; border-radius: 50%; background: var(--alarm-deep); color: #fff;
      display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; }
    q { quotes: '„' '”'; overflow-wrap: anywhere; }
    .meta { color: var(--muted); font-size: 14px; }
    .ignored strong, .ignored q { text-decoration: line-through; color: var(--muted); }
    .ignored .num { background: var(--muted); }
    .toggle { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; font-size: 15px; color: var(--muted); cursor: pointer; }
    .toggle input { width: 20px; height: 20px; }
  `,
})
export class StageTimeline {
  readonly alert = input.required<Alert>();
  readonly segments = input<TranscriptSegment[]>([]);
  readonly ignored = input<ReadonlySet<StageId>>(new Set());
  readonly canIgnore = input(true);
  readonly toggle = output<StageId>();

  protected readonly stages = computed(() =>
    stagesInOrder(this.alert(), this.segments()).map((hit) => {
      const seg = this.segments().find((s) => s.segId === hit.segId);
      return {
        stage: hit.stage,
        quote: hit.quote,
        time: seg ? formatDuration(seg.tStartMs / 1000) : null,
        source: HIT_SOURCE_LABEL[hit.source],
      };
    }),
  );
}

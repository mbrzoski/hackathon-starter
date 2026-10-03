import { Component, computed, input } from '@angular/core';
import { Alert, TranscriptSegment } from '../../api/model/models';
import { segmentStartSeconds } from '../../core/call-view';
import { StageLabelPipe } from '../../shared/stage-label.pipe';
import { formatDuration } from '../../shared/time';

/** Why the alert appeared (FE-08): Polish stage name, verbatim quote, time from the start of the call. */
@Component({
  selector: 'app-evidence-quotes',
  imports: [StageLabelPipe],
  template: `
    <ul>
      @for (e of evidence(); track e.key) {
        <li>
          <strong>{{ e.stage | stageLabel }}</strong>
          <q>{{ e.quote }}</q>
          @if (e.time) {
            <span class="time">{{ e.time }} od początku rozmowy</span>
          }
        </li>
      }
    </ul>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 16px; }
    li { display: grid; gap: 4px; background: rgba(0, 0, 0, 0.22); border-radius: 16px; padding: 14px 16px; }
    q { font-style: italic; quotes: '„' '”'; }
    .time { font-size: 28px; opacity: 0.95; }
  `,
})
export class EvidenceQuotes {
  readonly alert = input.required<Alert>();
  readonly segments = input<TranscriptSegment[]>([]);

  protected readonly evidence = computed(() =>
    this.alert()
      .stages.filter((hit) => hit.validated)
      .map((hit) => {
        const start = segmentStartSeconds(this.segments(), hit.segId);
        return {
          key: `${hit.stage}-${hit.segId}`,
          stage: hit.stage,
          quote: hit.quote,
          time: start === null ? null : formatDuration(start),
        };
      }),
  );
}

import { Component, computed, input } from '@angular/core';
import { Alert, TranscriptSegment } from '../../api/model/models';
import { findQuote } from '../../shared/quote-match';
import { formatDuration } from '../../shared/time';
import { excerpt } from './evidence';

const SPEAKER = { A: 'Mówca A', B: 'Mówca B', unknown: 'Nieznany mówca' } as const;

/** Cited segments with ±2 segments of context; each quote highlighted where it was said (CON-05 matching). */
@Component({
  selector: 'app-transcript-excerpt',
  template: `
    @for (row of rows(); track $index) {
      @if (row) {
        <div class="row" [class.cited]="row.cited">
          <span class="t">{{ time(row.segment.tStartMs) }}</span>
          <div>
            <div class="who">{{ speaker(row.segment.speaker) }}</div>
            <p>@for (part of row.parts; track $index) {@if (part.quote) {<mark>{{ part.text }}</mark>} @else {<span>{{ part.text }}</span>}}</p>
          </div>
        </div>
      } @else {
        <div class="gap" aria-hidden="true">…</div>
      }
    }
  `,
  styles: `
    .row { display: grid; grid-template-columns: 48px 1fr; gap: 8px; padding: 8px 0; border-top: 1px solid var(--bg); }
    .row:not(.cited) p { color: var(--muted); }
    .t { color: var(--muted); font-variant-numeric: tabular-nums; }
    .who { font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--navy); }
    p { margin: 2px 0 0; overflow-wrap: anywhere; }
    mark { background: var(--highlight); color: var(--text); padding: 1px 3px; border-radius: 3px; }
    .gap { color: var(--muted); padding: 2px 0 2px 56px; }
  `,
})
export class TranscriptExcerpt {
  readonly alert = input.required<Alert>();
  readonly segments = input.required<TranscriptSegment[]>();

  protected readonly rows = computed(() => excerpt(this.alert(), this.segments(), findQuote));
  protected readonly time = (ms: number) => formatDuration(ms / 1000);
  protected readonly speaker = (s: keyof typeof SPEAKER) => SPEAKER[s] ?? SPEAKER.unknown;
}

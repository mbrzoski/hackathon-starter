import { Component, computed, effect, ElementRef, input, viewChild } from '@angular/core';
import { Alert, StageHitSourceEnum, TranscriptSegment } from '../api/model/models';
import { findQuote } from './quote-match';
import { formatDuration } from './time';

/** A keyword hit to underline inside one segment. */
export interface Highlight {
  segId: string;
  quote: string;
}

interface Part {
  text: string;
  hit: boolean;
}

/**
 * Keyword hits of the alerts, ready to underline. The backend sends keyword hits only inside alerts, so a hit that
 * did not raise an alert is not shown here.
 */
export function keywordHighlights(alerts: readonly Alert[]): Highlight[] {
  return alerts.flatMap((alert) =>
    alert.stages
      .filter((hit) => hit.source === StageHitSourceEnum.keywords)
      .map((hit) => ({ segId: hit.segId, quote: hit.quote })),
  );
}

/** Splits a segment text into plain and underlined parts. Overlapping or missing quotes are skipped. */
export function highlightParts(text: string, quotes: readonly string[]): Part[] {
  const ranges = quotes
    .map((quote) => findQuote(text, quote))
    .filter((r): r is NonNullable<typeof r> => r !== null)
    .sort((a, b) => a.start - b.start);
  const parts: Part[] = [];
  let at = 0;
  for (const range of ranges) {
    if (range.start < at) {
      continue;
    }
    if (range.start > at) {
      parts.push({ text: text.slice(at, range.start), hit: false });
    }
    parts.push({ text: text.slice(range.start, range.end), hit: true });
    at = range.end;
  }
  if (at < text.length) {
    parts.push({ text: text.slice(at), hit: false });
  }
  return parts;
}

/**
 * Live transcript for the team: every segment with its id and time, interim in grey italics, final in normal type,
 * keyword hits underlined. Memory only: nothing is stored (rule 4 of CLAUDE.md).
 */
@Component({
  selector: 'app-transcript-debug-panel',
  template: `
    <section class="panel" aria-label="Transkrypcja na żywo (dla zespołu)">
      <h2>Transkrypcja na żywo</h2>
      <div class="list" #list>
        @for (row of rows(); track row.segment.segId) {
          <div class="row" [class.interim]="!row.segment.isFinal">
            <span class="meta">{{ row.segment.segId }} · {{ time(row.segment.tStartMs) }}@if (!row.segment.isFinal) { · wstępny}</span>
            <p>@for (part of row.parts; track $index) {@if (part.hit) {<u>{{ part.text }}</u>} @else {<span>{{ part.text }}</span>}}</p>
          </div>
        } @empty {
          <p class="empty">Brak segmentów. Pojawią się po rozpoczęciu rozmowy.</p>
        }
      </div>
    </section>
  `,
  styles: `
    .panel { display: flex; flex-direction: column; min-height: 0; max-height: inherit; font-size: 16px; line-height: 1.4;
      background: var(--surface); color: var(--text); }
    h2 { font-size: 16px; margin: 0 0 6px; }
    .list { overflow-y: auto; min-height: 0; }
    .row { padding: 6px 0; border-top: 1px solid var(--border); }
    .meta { font-size: 13px; color: var(--muted); font-variant-numeric: tabular-nums; }
    p { margin: 2px 0 0; overflow-wrap: anywhere; }
    .interim p { color: var(--muted); font-style: italic; }
    u { text-decoration: underline 3px var(--warn); text-underline-offset: 3px; font-weight: 700; }
    .empty { color: var(--muted); }
  `,
})
export class TranscriptDebugPanel {
  readonly segments = input.required<readonly TranscriptSegment[]>();
  readonly highlights = input<readonly Highlight[]>([]);

  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  protected readonly rows = computed(() => {
    const highlights = this.highlights();
    return this.segments().map((segment) => ({
      segment,
      parts: highlightParts(
        segment.text,
        highlights.filter((h) => h.segId === segment.segId).map((h) => h.quote),
      ),
    }));
  });
  protected readonly time = (ms: number) => formatDuration(ms / 1000);

  constructor() {
    // Follow the live end of the transcript.
    effect(() => {
      this.rows();
      const el = this.list()?.nativeElement;
      if (el) {
        queueMicrotask(() => (el.scrollTop = el.scrollHeight));
      }
    });
  }
}

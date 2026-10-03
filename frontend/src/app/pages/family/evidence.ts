import { Alert, StageHit, StageId, TranscriptSegment } from '../../api/model/models';

/** Validated hits, first per stage, in the order they were said. */
export function stagesInOrder(alert: Alert, segments: TranscriptSegment[]): StageHit[] {
  const start = new Map(segments.map((s) => [s.segId, s.tStartMs]));
  // Without the transcript, s3 < s10 still gives the order of the call.
  const order = (hit: StageHit) => start.get(hit.segId) ?? Number(hit.segId.slice(1)) * 1e9;
  const seen = new Set<StageId>();
  return alert.stages
    .filter((hit) => hit.validated)
    .sort((a, b) => order(a) - order(b))
    .filter((hit) => !seen.has(hit.stage) && seen.add(hit.stage));
}

export interface ExcerptPart {
  text: string;
  quote: boolean;
}

export interface ExcerptRow {
  segment: TranscriptSegment;
  cited: boolean;
  parts: ExcerptPart[];
}

/** Cited segments with up to two segments of context on each side; gaps between blocks are null. */
export function excerpt(alert: Alert, segments: TranscriptSegment[], findRange: RangeFinder): (ExcerptRow | null)[] {
  const ordered = [...segments].sort((a, b) => a.tStartMs - b.tStartMs);
  const hits = alert.stages.filter((h) => h.validated);
  const cited = new Set(hits.map((h) => h.segId));
  const keep = new Set<number>();
  ordered.forEach((s, i) => {
    if (cited.has(s.segId)) for (let j = i - 2; j <= i + 2; j++) if (j >= 0 && j < ordered.length) keep.add(j);
  });
  const rows: (ExcerptRow | null)[] = [];
  let last = -1;
  for (const i of [...keep].sort((a, b) => a - b)) {
    if (last >= 0 && i > last + 1) rows.push(null);
    const segment = ordered[i];
    const ranges = hits
      .filter((h) => h.segId === segment.segId)
      .map((h) => findRange(segment.text, h.quote))
      .filter((r): r is { start: number; end: number } => r !== null);
    rows.push({ segment, cited: cited.has(segment.segId), parts: split(segment.text, ranges) });
    last = i;
  }
  return rows;
}

export type RangeFinder = (text: string, quote: string) => { start: number; end: number } | null;

function split(text: string, ranges: { start: number; end: number }[]): ExcerptPart[] {
  const merged = [...ranges].sort((a, b) => a.start - b.start).reduce<{ start: number; end: number }[]>((acc, r) => {
    const prev = acc[acc.length - 1];
    if (prev && r.start <= prev.end) prev.end = Math.max(prev.end, r.end);
    else acc.push({ ...r });
    return acc;
  }, []);
  const parts: ExcerptPart[] = [];
  let at = 0;
  for (const r of merged) {
    if (r.start > at) parts.push({ text: text.slice(at, r.start), quote: false });
    parts.push({ text: text.slice(r.start, r.end), quote: true });
    at = r.end;
  }
  if (at < text.length) parts.push({ text: text.slice(at), quote: false });
  return parts;
}

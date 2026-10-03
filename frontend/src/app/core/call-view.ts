import { Alert, Decision, StageHit, TranscriptSegment } from '../api/model/models';

/** The alert still waiting for a decision from this actor, if any (newest first). */
export function openAlert(alerts: Alert[], decisions: Decision[], actor: Decision['actor']): Alert | null {
  const decided = new Set(decisions.filter((d) => d.actor === actor).map((d) => d.alertId));
  return [...alerts].reverse().find((a) => !decided.has(a.alertId)) ?? null;
}

export function segmentStartSeconds(segments: TranscriptSegment[], segId: string): number | null {
  const seg = segments.find((s) => s.segId === segId);
  return seg ? Math.floor(seg.tStartMs / 1000) : null;
}

export const HIT_SOURCE_LABEL: Record<StageHit['source'], string> = { llm: 'AI', keywords: 'słowa kluczowe' };
export const TRIGGERED_BY_LABEL: Record<Alert['triggeredBy'], string> = {
  llm: 'AI',
  keywords: 'słowa kluczowe',
  both: 'AI + słowa kluczowe',
};

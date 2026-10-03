// UI-only view models (not part of the integration contract).
import { Alert, Mode, RiskLevel, StageHit, StageId, TranscriptSegment } from './contracts';

/** Polish display names of the stages. */
export const STAGE_LABELS: Record<StageId, string> = {
  AUTHORITY_CLAIM: 'Podanie się za funkcjonariusza',
  URGENT_THREAT: 'Wywołanie zagrożenia',
  SECRECY_DEMAND: 'Nakaz tajemnicy',
  ISOLATION: 'Izolowanie rozmówcy',
  MONEY_REQUEST: 'Żądanie gotówki',
  PAYMENT_CHANNEL: 'Instrukcja przekazania pieniędzy',
  REMOTE_ACCESS: 'Dostęp zdalny do urządzenia',
  PERSONAL_DATA_REQUEST: 'Prośba o dane osobowe',
};

/** Stages shown in the family panel list, in the order of the "na policjanta" script. */
export const SCRIPT_STAGES: StageId[] = [
  'AUTHORITY_CLAIM',
  'URGENT_THREAT',
  'SECRECY_DEMAND',
  'MONEY_REQUEST',
  'PAYMENT_CHANNEL',
];

export interface CallInfo {
  callerNumberMasked: string;
  isKnownContact: boolean;
  startedAt: string; // "12:43"
  durationSeconds: number;
}

export interface SettingsContact {
  name: string;
  relation: string;
  phone: string; // the only source of numbers to dial (FE-10)
}

export interface Settings {
  senior: { name: string; displayName: string; phone: string };
  contacts: SettingsContact[];
  emergencyNumber: string;
}

export interface FamilyMessage {
  from: string;
  text: string;
  receivedAt: string;
  hasPhoto: boolean;
}

/** A stage hit plus UI-only evidence details. Level and stage come from the backend, not computed here. */
export interface StageEvidence {
  hit: StageHit;
  tMs: number;
  detectedBy: 'llm' | 'keywords' | 'both';
}

export interface FamilyDashboard {
  mode: Mode;
  connected: boolean;
  alert: Alert;
  alertTitle: string;
  alertText: string;
  alertTime: string;
  call: CallInfo;
  level: RiskLevel;
  segments: TranscriptSegment[];
  stages: StageEvidence[];
}

export interface SeniorState {
  alertHeadline: string;
  alertReason: string;
  steps: { title: string; text: string }[];
  message: FamilyMessage;
}

export interface ListenState {
  call: CallInfo;
  level: RiskLevel;
  alertTitle: string;
  alertReason: string;
  quote: string;
}

/** 65 -> "01:05" */
export function formatDuration(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

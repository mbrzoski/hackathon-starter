// TEMPORARY: FE-02 requires types from the contracts package, which does not exist yet. Replace with @aniol/contracts.
// Copied 1:1 from docs/architecture.md, section "INTEGRATION CONTRACTS".

export type StageId =
  | 'AUTHORITY_CLAIM' // police, prosecutor, bank security, CBŚ
  | 'URGENT_THREAT' // relative in trouble, savings at risk, criminal in the bank
  | 'SECRECY_DEMAND' // don't tell family / bank staff
  | 'ISOLATION' // stay on the line, don't call anyone, call 112 without hanging up
  | 'MONEY_REQUEST' // withdraw, transfer, hand over
  | 'PAYMENT_CHANNEL' // BLIK code, "safe account", courier, crypto ATM
  | 'REMOTE_ACCESS' // install an app, read out codes
  | 'PERSONAL_DATA_REQUEST'; // PESEL, card number, PIN

export interface TranscriptSegment {
  callId: string;
  segId: string; // "s12"
  tStartMs: number;
  tEndMs: number;
  text: string;
  isFinal: boolean;
  speaker: 'A' | 'B' | 'unknown'; // only if STT diarization provides it
  sttConfidence?: number; // only if the STT provider returns it
}

export interface StageHit {
  stage: StageId;
  segId: string;
  quote: string;
  speakerRole: 'caller' | 'senior' | 'background' | 'unclear';
  source: 'llm' | 'keywords';
  validated: boolean; // QuoteValidator result
}

export type RiskLevel = 'none' | 'low' | 'medium' | 'high';
export type Mode = 'LIVE' | 'REPLAY' | 'SCRIPTED' | 'MOCK';

export interface Alert {
  alertId: string;
  callId: string;
  level: RiskLevel;
  stages: StageHit[]; // validated only
  templateId: string; // deterministic text shown/spoken
  triggeredBy: 'llm' | 'keywords' | 'both';
  createdAt: string;
  mode: Mode;
}

export interface Decision {
  alertId: string;
  actor: 'senior' | 'family';
  decision: 'hung_up' | 'called_trusted' | 'false_alarm' | 'confirmed_scam';
  at: string;
}

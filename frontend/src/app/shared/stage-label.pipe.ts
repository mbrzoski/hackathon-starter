import { Pipe, PipeTransform } from '@angular/core';
import { StageId } from '../api/model/models';

export const STAGE_LABELS: Record<StageId, string> = {
  AUTHORITY_CLAIM: 'Podanie się za funkcjonariusza',
  URGENT_THREAT: 'Wywołanie zagrożenia',
  SECRECY_DEMAND: 'Prośba o tajemnicę',
  ISOLATION: 'Odcięcie od bliskich',
  MONEY_REQUEST: 'Żądanie pieniędzy',
  PAYMENT_CHANNEL: 'Sposób przekazania pieniędzy',
  REMOTE_ACCESS: 'Zdalny dostęp do urządzenia',
  PERSONAL_DATA_REQUEST: 'Prośba o dane osobowe',
  FAMILY_KEYWORD: 'Słowo wskazane przez rodzinę',
};

/** StageId -> Polish name shown to people. */
@Pipe({ name: 'stageLabel' })
export class StageLabelPipe implements PipeTransform {
  transform(stage: StageId): string {
    return STAGE_LABELS[stage] ?? stage;
  }
}

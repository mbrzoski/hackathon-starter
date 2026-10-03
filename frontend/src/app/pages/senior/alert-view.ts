import { Component, computed, input, output } from '@angular/core';
import { Alert, DecisionRequestDecisionEnum } from '../../api/model/models';
import { Icon } from '../../shared/icon';
import { DecisionButtons } from './decision-buttons';
import { VoiceReadout } from './voice-readout';

const SOURCE_LABEL: Record<Alert['triggeredBy'], string> = {
  llm: 'wykryte przez AI',
  keywords: 'wykryte po słowach kluczowych',
  both: 'AI i słowa kluczowe',
};

/**
 * Red alert screen. Texts come from the backend template (FE-04). The page never scrolls and the decisions are always
 * on screen; if the text does not fit a small phone, only that area scrolls (WEB-11).
 */
@Component({
  selector: 'app-alert-view',
  imports: [DecisionButtons, Icon, VoiceReadout],
  template: `
    <section class="alert" role="alert" aria-live="assertive">
      <div class="body">
      <div class="head">
        <p class="eyebrow"><app-icon name="warning" [size]="40" /> OSTRZEŻENIE</p>
        <p class="text">{{ alert().shortText }}</p>
        <p class="advice">{{ alert().advice }}</p>
        <p class="source">{{ source() }}</p>
      </div>
      <div class="tools">
        <app-voice-readout [text]="spoken()" [key]="alert().alertId" />
      </div>
      </div>
      <app-decision-buttons [contactName]="contactName()" (decide)="decide.emit($event)" (emergency)="emergency.emit()" />
    </section>
  `,
  styleUrl: './alert-view.scss',
})
export class AlertView {
  readonly alert = input.required<Alert>();
  readonly contactName = input<string | null>(null);
  readonly decide = output<DecisionRequestDecisionEnum>();
  readonly emergency = output<void>();

  protected readonly source = computed(() => SOURCE_LABEL[this.alert().triggeredBy]);
  /** The backend template is read as shortText, then advice, in one utterance (FF-12). */
  protected readonly spoken = computed(() => `${this.alert().shortText} ${this.alert().advice}`);
}

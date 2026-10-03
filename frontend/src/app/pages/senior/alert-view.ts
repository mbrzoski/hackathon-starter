import { Component, computed, input, output, signal } from '@angular/core';
import { Alert, DecisionRequestDecisionEnum, TranscriptSegment } from '../../api/model/models';
import { Icon } from '../../shared/icon';
import { DecisionButtons } from './decision-buttons';
import { EvidenceQuotes } from './evidence-quotes';
import { VoiceReadout } from './voice-readout';

const SOURCE_LABEL: Record<Alert['triggeredBy'], string> = {
  llm: 'wykryte przez AI',
  keywords: 'wykryte po słowach kluczowych',
  both: 'AI i słowa kluczowe',
};

/**
 * Red alert screen. Texts come from the backend template (FE-04). The page never scrolls and the decisions are always
 * on screen; if the text and "Dlaczego?" do not fit a small phone, only that area scrolls (WEB-11).
 */
@Component({
  selector: 'app-alert-view',
  imports: [DecisionButtons, EvidenceQuotes, Icon, VoiceReadout],
  template: `
    <section class="alert" role="alert" aria-live="assertive">
      <div class="body">
      <div class="head">
        <p class="eyebrow"><app-icon name="warning" [size]="40" /> OSTRZEŻENIE</p>
        <p class="text">{{ alert().shortText }}</p>
        <p class="source">{{ source() }}</p>
      </div>
      <div class="tools">
        <app-voice-readout [text]="alert().shortText" [key]="alert().alertId" />
        @if (alert().stages.length) {
          <button type="button" class="why" [attr.aria-expanded]="showWhy()" (click)="showWhy.update((v) => !v)">
            {{ showWhy() ? 'Ukryj' : 'Dlaczego?' }}
          </button>
        }
      </div>
      @if (showWhy()) {
        <app-evidence-quotes [alert]="alert()" [segments]="segments()" />
      }
      </div>
      <app-decision-buttons [contactName]="contactName()" (decide)="decide.emit($event)" />
    </section>
  `,
  styleUrl: './alert-view.scss',
})
export class AlertView {
  readonly alert = input.required<Alert>();
  readonly segments = input<TranscriptSegment[]>([]);
  readonly contactName = input<string | null>(null);
  readonly decide = output<DecisionRequestDecisionEnum>();

  protected readonly showWhy = signal(false);
  protected readonly source = computed(() => SOURCE_LABEL[this.alert().triggeredBy]);
}

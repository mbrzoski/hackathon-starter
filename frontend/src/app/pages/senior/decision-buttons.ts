import { Component, computed, input, output } from '@angular/core';
import { DecisionRequestDecisionEnum } from '../../api/model/models';
import { Icon } from '../../shared/icon';

/** The senior's three choices: full width, min. 72 px high (WEB-11). Nothing is dialled or hung up here (FE-09). */
@Component({
  selector: 'app-decision-buttons',
  imports: [Icon],
  template: `
    <button type="button" class="decision primary" (click)="decide.emit(D.hung_up)">Rozłączam się</button>
    <button type="button" class="decision" (click)="decide.emit(D.called_trusted)">
      <app-icon name="phone" [size]="32" /> {{ callLabel() }}
    </button>
    <button type="button" class="decision ghost" (click)="decide.emit(D.false_alarm)">To fałszywy alarm</button>
  `,
  styles: `
    :host { display: grid; gap: 12px; }
    .decision { font: inherit; font-size: 28px; font-weight: 800; min-height: 72px; width: 100%; border-radius: 16px;
      display: flex; align-items: center; justify-content: center; gap: 12px; cursor: pointer;
      background: #fff; color: var(--primary); border: 3px solid #fff; }
    .decision.primary { color: var(--alarm-deep); }
    .decision.ghost { background: transparent; color: #fff; }
    .decision:focus-visible { outline: 4px solid var(--highlight); outline-offset: 2px; }
  `,
})
export class DecisionButtons {
  protected readonly D = DecisionRequestDecisionEnum;
  /** Name of the first trusted contact from settings, if known. */
  readonly contactName = input<string | null>(null);
  readonly decide = output<DecisionRequestDecisionEnum>();

  protected readonly callLabel = computed(() => {
    const name = this.contactName();
    return name ? `Zadzwoń do: ${name}` : 'Zadzwoń do bliskiej osoby';
  });
}

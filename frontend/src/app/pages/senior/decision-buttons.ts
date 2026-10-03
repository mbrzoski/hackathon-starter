import { Component, computed, input, output } from '@angular/core';
import { DecisionRequestDecisionEnum } from '../../api/model/models';
import { Icon } from '../../shared/icon';

/**
 * The senior's choices: full width, min. 72 px high (WEB-11). Nothing is dialled or hung up by the app (FE-09):
 * "Zadzwoń na 112" is a link that only dials after the senior taps it.
 */
@Component({
  selector: 'app-decision-buttons',
  imports: [Icon],
  template: `
    <button type="button" class="decision primary" (click)="decide.emit(D.hung_up)">Rozłączam się</button>
    <button type="button" class="decision" (click)="decide.emit(D.called_trusted)">
      <app-icon name="phone" [size]="32" /> {{ callLabel() }}
    </button>
    <a class="decision" href="tel:112" (click)="emergency.emit()"><app-icon name="phone" [size]="32" /> Zadzwoń na 112</a>
  `,
  styles: `
    :host { display: grid; gap: 12px; }
    .decision { font: inherit; font-size: 28px; font-weight: 800; min-height: 72px; width: 100%; border-radius: 16px;
      display: flex; align-items: center; justify-content: center; gap: 12px; cursor: pointer;
      background: #fff; color: var(--primary); border: 3px solid #fff; }
    .decision.primary { color: var(--alarm-deep); }
    a.decision { text-decoration: none; box-sizing: border-box; }
    .decision:focus-visible { outline: 4px solid var(--highlight); outline-offset: 2px; }
  `,
})
export class DecisionButtons {
  protected readonly D = DecisionRequestDecisionEnum;
  /** Name of the first trusted contact from settings, if known. */
  readonly contactName = input<string | null>(null);
  readonly decide = output<DecisionRequestDecisionEnum>();
  /** The senior tapped "Zadzwoń na 112" (the link itself dials; the app only ends the simulated call). */
  readonly emergency = output<void>();

  protected readonly callLabel = computed(() => {
    const name = this.contactName();
    return name ? `Zadzwoń do: ${name}` : 'Zadzwoń do bliskiej osoby';
  });
}

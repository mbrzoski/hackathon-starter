import { Component, computed, input, output } from '@angular/core';
import { DecisionRequestDecisionEnum } from '../../api/model/models';
import { TrustedContact } from '../../core/settings.store';
import { Icon } from '../../shared/icon';

/** The family's actions. The call link comes from settings only (FE-10) and dials only on a tap (FE-09). */
@Component({
  selector: 'app-decision-bar',
  imports: [Icon],
  template: `
    @if (senior()?.phone; as phone) {
      <a class="btn-secondary btn-block" [href]="'tel:' + phone"><app-icon name="phone" [size]="22" /> {{ callLabel() }}</a>
    } @else {
      <p class="note">Numer seniora nie jest zapisany w ustawieniach.</p>
    }
    <div class="row">
      <button type="button" class="btn-primary" [disabled]="disabled()" (click)="decide.emit(D.confirmed_scam)">
        Potwierdzam oszustwo
      </button>
      <button type="button" class="btn-neutral" [disabled]="disabled()" (click)="decide.emit(D.false_alarm)">
        Fałszywy alarm
      </button>
    </div>
  `,
  styles: `
    :host { display: grid; gap: 10px; }
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .note { margin: 0; color: var(--muted); font-size: 14px; }
    @media (max-width: 400px) { .row { grid-template-columns: 1fr; } }
  `,
})
export class DecisionBar {
  protected readonly D = DecisionRequestDecisionEnum;
  readonly senior = input<TrustedContact | null>(null);
  readonly disabled = input(false);
  readonly decide = output<DecisionRequestDecisionEnum>();

  protected readonly callLabel = computed(() => `Zadzwoń do: ${this.senior()?.name ?? 'seniora'}`);
}

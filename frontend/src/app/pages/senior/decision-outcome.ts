import { Component, input, output } from '@angular/core';
import { Alert } from '../../api/model/models';
import { TrustedContact } from '../../core/settings.store';
import { Icon } from '../../shared/icon';

/** What the senior sees after "Rozłączam się" (advice) or "Zadzwoń do…" (the saved number, FE-10). */
@Component({
  selector: 'app-decision-outcome',
  imports: [Icon],
  template: `
    <section class="outcome" aria-live="polite">
      <div class="body">
      @if (kind() === 'hung_up') {
        <app-icon name="check" [size]="72" />
        <p class="big">{{ alert().advice }}</p>
      } @else {
        @if (contact(); as c) {
          @if (c.name) {
            <p>Zadzwoń do: <strong>{{ c.name }}</strong></p>
          } @else {
            <p>Zadzwoń do bliskiej osoby</p>
          }
          <p class="number">{{ c.phone }}</p>
          <!-- FE-09: nothing is dialled until the senior taps the link. -->
          <a class="decision call" [href]="'tel:' + c.phone"><app-icon name="phone" [size]="32" /> Zadzwoń teraz</a>
        } @else {
          <p class="big">Brak zapisanego numeru.</p>
          <p>Rodzina może go dodać w ustawieniach.</p>
        }
      }
      </div>
      <button type="button" class="decision" (click)="done.emit()">Gotowe</button>
    </section>
  `,
  styles: `
    :host { flex: 1; min-height: 0; display: flex; }
    .outcome { flex: 1; min-height: 0; display: grid; grid-template-rows: minmax(0, 1fr) auto; gap: 16px; padding: 16px;
      border-radius: 24px; background: var(--bg); color: var(--text); }
    .body { min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 16px; justify-content: safe center; }
    p { margin: 0; }
    .big { font-size: clamp(32px, 5.5cqmin, 56px); line-height: 1.2; font-weight: 800; }
    .number { font-size: clamp(44px, 9cqmin, 80px); font-weight: 800; letter-spacing: 0.04em; font-variant-numeric: tabular-nums; }
    .decision { font: inherit; font-size: 28px; font-weight: 800; min-height: 72px; width: 100%; border-radius: 16px;
      display: flex; align-items: center; justify-content: center; gap: 12px; cursor: pointer; text-decoration: none;
      background: var(--surface); color: var(--primary); border: 3px solid var(--primary); }
    .decision.call { background: var(--primary); color: #fff; }
    .decision:focus-visible { outline: 4px solid var(--text); outline-offset: 2px; }
  `,
})
export class DecisionOutcome {
  readonly kind = input.required<'hung_up' | 'called_trusted'>();
  readonly alert = input.required<Alert>();
  readonly contact = input<TrustedContact | null>(null);
  readonly done = output<void>();
}

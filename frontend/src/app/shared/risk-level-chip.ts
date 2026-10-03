import { Component, computed, input } from '@angular/core';
import { RiskLevel } from '../api/model/models';

export const RISK_LEVEL_WORDS: Record<RiskLevel, string> = {
  none: 'Brak',
  low: 'Niskie',
  medium: 'Średnie',
  high: 'Wysokie',
};

/** Risk level in words only, never a percentage (FE-07). The level itself comes from the backend (FE-04). */
@Component({
  selector: 'app-risk-level-chip',
  template: `<span class="chip" [class]="level()">{{ word() }}</span>`,
  styles: `
    .chip { display: inline-block; padding: 4px 14px; border-radius: 8px; font-weight: 700; }
    .none { background: var(--safe-bg); color: var(--safe); }
    .low { background: var(--highlight); color: #4a3500; }
    .medium { background: var(--warn-bg); color: var(--warn); }
    .high { background: var(--alarm-bg); color: var(--alarm); }
  `,
})
export class RiskLevelChip {
  readonly level = input.required<RiskLevel>();
  protected readonly word = computed(() => RISK_LEVEL_WORDS[this.level()]);
}

import { Component, computed, inject, input, signal } from '@angular/core';
import {
  Decision,
  DecisionActorEnum,
  DecisionDecisionEnum,
  DecisionRequestActorEnum,
  DecisionRequestDecisionEnum,
  StageId,
  TranscriptSegment,
} from '../../api/model/models';
import { TRIGGERED_BY_LABEL } from '../../core/call-view';
import { DecisionOutbox } from '../../core/decision-outbox';
import { SettingsStore } from '../../core/settings.store';
import { RiskLevelChip } from '../../shared/risk-level-chip';
import { formatClock } from '../../shared/time';
import { DecisionBar } from './decision-bar';
import { FamilyAlert } from './family-feed';
import { StageTimeline } from './stage-timeline';
import { TranscriptExcerpt } from './transcript-excerpt';

const SENIOR_CHOICE: Record<DecisionDecisionEnum, string> = {
  hung_up: 'rozłączam się',
  called_trusted: 'dzwonię do bliskiej osoby',
  false_alarm: 'to fałszywy alarm',
  confirmed_scam: 'to oszustwo',
};
const FAMILY_CHOICE: Record<DecisionDecisionEnum, string> = {
  hung_up: 'rozłączenie',
  called_trusted: 'telefon do bliskiej osoby',
  false_alarm: 'fałszywy alarm',
  confirmed_scam: 'potwierdzone oszustwo',
};

/** One alert for the family: level in words (FE-07), evidence (FE-08), what the senior chose, and the decision bar. */
@Component({
  selector: 'app-alert-card',
  imports: [DecisionBar, RiskLevelChip, StageTimeline, TranscriptExcerpt],
  templateUrl: './alert-card.html',
  styleUrl: './alert-card.scss',
})
export class AlertCard {
  private readonly outbox = inject(DecisionOutbox);
  protected readonly settings = inject(SettingsStore);

  readonly item = input.required<FamilyAlert>();
  /** Transcript of this alert's call when it is the ongoing call; empty for history. */
  readonly segments = input<TranscriptSegment[]>([]);
  /** True while the alert's call is still ongoing: only then the backend accepts ignored stages (FF-14). */
  readonly live = input(false);

  protected readonly ignored = signal<ReadonlySet<StageId>>(new Set());
  /** Sent from this card, waiting for alert.decision from the backend. */
  protected readonly sent = signal<DecisionRequestDecisionEnum | null>(null);
  /** Why the last decision from this card was not saved; the buttons are enabled again (FF-14). */
  protected readonly failure = signal<string | null>(null);

  protected readonly alert = computed(() => this.item().alert);
  protected readonly time = computed(() => formatClock(this.alert().createdAt));
  protected readonly source = computed(() => TRIGGERED_BY_LABEL[this.alert().triggeredBy]);

  protected readonly seniorDecision = computed(() => this.latest(DecisionActorEnum.senior));
  protected readonly familyDecision = computed(() => this.latest(DecisionActorEnum.family));
  protected readonly seniorText = computed(() => {
    const d = this.seniorDecision();
    if (!d) return null;
    const name = this.settings.senior()?.name;
    return `${name ? `${name} wybrał(a)` : 'Senior wybrał(a)'}: ${SENIOR_CHOICE[d.decision]}`;
  });
  protected readonly familyText = computed(() => {
    const d = this.familyDecision();
    return d ? `Rodzina: ${FAMILY_CHOICE[d.decision]}` : null;
  });
  protected readonly pending = computed(() => !!this.sent() && !this.familyDecision());
  protected readonly canIgnore = computed(() => this.live() && !this.familyDecision());

  protected readonly clock = formatClock;

  protected toggle(stage: StageId): void {
    this.ignored.update((set) => {
      const next = new Set(set);
      if (!next.delete(stage)) next.add(stage);
      return next;
    });
  }

  protected decide(decision: DecisionRequestDecisionEnum): void {
    this.sent.set(decision);
    this.failure.set(null);
    const ignored = this.canIgnore() ? [...this.ignored()] : [];
    this.outbox.send(this.alert().alertId, decision, DecisionRequestActorEnum.family, ignored, (reason) => {
      this.sent.set(null);
      this.failure.set(reason);
    });
  }

  private latest(actor: DecisionActorEnum): Decision | null {
    const list = this.item().decisions.filter((d) => d.actor === actor);
    return list[list.length - 1] ?? null;
  }
}

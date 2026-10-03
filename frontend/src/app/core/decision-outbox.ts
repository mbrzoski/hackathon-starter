import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { AlertsService } from '../api/api/alerts.service';
import { DecisionRequest, DecisionRequestActorEnum, DecisionRequestDecisionEnum, StageId } from '../api/model/models';
import { problemDetailText } from '../shared/problem-detail';

const FIRST_RETRY_MS = 2000;
const MAX_RETRY_MS = 30_000;
/** 2 + 4 + 8 + 16 s, then every 30 s: about 4 minutes in all. */
const MAX_ATTEMPTS = 12;

/**
 * Sends decisions (POST /api/alerts/{id}/decision), by default as the senior. The screen never waits for it:
 * network and server errors are retried in the background up to MAX_ATTEMPTS. A 4xx is final.
 * A decision that is not saved in the end is never silent: `unsaved` turns on until acknowledged (FF-11).
 */
@Injectable({ providedIn: 'root' })
export class DecisionOutbox {
  private readonly alerts = inject(AlertsService);
  private readonly _unsaved = signal(false);

  /** True once a decision has been rejected or given up on, until the senior acknowledges it. */
  readonly unsaved = this._unsaved.asReadonly();

  send(
    alertId: string,
    decision: DecisionRequestDecisionEnum,
    actor: DecisionRequestActorEnum = DecisionRequestActorEnum.senior,
    ignoredStages: readonly StageId[] = [],
  ): void {
    const body: DecisionRequest = { actor, decision };
    if (ignoredStages.length) {
      // The generator types uniqueItems as Set, which JSON.stringify turns into {}: send a plain array.
      body.ignoredStages = [...new Set(ignoredStages)] as unknown as Set<StageId>;
    }
    this.attempt(alertId, body, 1);
  }

  acknowledge(): void {
    this._unsaved.set(false);
  }

  private attempt(alertId: string, body: DecisionRequest, attempt: number): void {
    const decision = body.decision;
    this.alerts.submitDecision(alertId, body).subscribe({
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status >= 400 && err.status < 500) {
          console.warn(`Decision ${decision} for ${alertId} rejected: ${problemDetailText(err)}`);
          this._unsaved.set(true);
          return;
        }
        if (attempt >= MAX_ATTEMPTS) {
          console.warn(`Decision ${decision} for ${alertId} not saved after ${attempt} attempts`);
          this._unsaved.set(true);
          return;
        }
        const delay = Math.min(FIRST_RETRY_MS * 2 ** (attempt - 1), MAX_RETRY_MS);
        setTimeout(() => this.attempt(alertId, body, attempt + 1), delay);
      },
    });
  }
}

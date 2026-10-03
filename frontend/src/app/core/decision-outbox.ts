import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AlertsService } from '../api/api/alerts.service';
import { DecisionRequestActorEnum, DecisionRequestDecisionEnum } from '../api/model/models';
import { problemDetailText } from '../shared/problem-detail';

const FIRST_RETRY_MS = 2000;
const MAX_RETRY_MS = 30_000;

/**
 * Sends the senior's decisions (POST /api/alerts/{id}/decision). The screen never waits for it:
 * network and server errors are retried in the background; a 4xx is final and only logged.
 */
@Injectable({ providedIn: 'root' })
export class DecisionOutbox {
  private readonly alerts = inject(AlertsService);

  send(alertId: string, decision: DecisionRequestDecisionEnum): void {
    this.attempt(alertId, decision, 0);
  }

  private attempt(alertId: string, decision: DecisionRequestDecisionEnum, retry: number): void {
    this.alerts.submitDecision(alertId, { actor: DecisionRequestActorEnum.senior, decision }).subscribe({
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status >= 400 && err.status < 500) {
          console.warn(`Decision ${decision} for ${alertId} rejected: ${problemDetailText(err)}`);
          return;
        }
        const delay = Math.min(FIRST_RETRY_MS * 2 ** retry, MAX_RETRY_MS);
        setTimeout(() => this.attempt(alertId, decision, retry + 1), delay);
      },
    });
  }
}

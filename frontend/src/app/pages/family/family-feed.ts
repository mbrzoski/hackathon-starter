import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { AlertsService } from '../../api/api/alerts.service';
import { Alert, Decision, DecisionActorEnum, RiskLevel } from '../../api/model/models';
import { EventsService } from '../../core/events.service';
import { problemDetailText } from '../../shared/problem-detail';
import { AlertNotifier } from './alert-notifier';

export interface FamilyAlert {
  alert: Alert;
  decisions: Decision[];
}

export const FAMILY_TITLE = 'Anioł Stróż · Panel rodziny';
export const ALERT_TITLE = '⚠ Alert';

const decisionKey = (d: Decision) => `${d.alertId}|${d.actor}|${d.decision}|${d.at}`;

/**
 * Alerts for the family panel: history from GET /api/alerts plus everything arriving on /ws/events, newest first.
 * Events of earlier calls are kept here even after EventsService clears them on call.started.
 */
@Injectable({ providedIn: 'root' })
export class FamilyFeed {
  private readonly api = inject(AlertsService);
  private readonly events = inject(EventsService);
  private readonly notifier = inject(AlertNotifier);
  private readonly title = inject(Title);

  private readonly alerts = signal<ReadonlyMap<string, Alert>>(new Map());
  private readonly decisions = signal<ReadonlyMap<string, Decision>>(new Map());
  /** Live high alerts the family has not decided on yet: they keep "⚠ Alert" in the tab title. */
  private readonly attention = signal<ReadonlySet<string>>(new Set());
  private loaded = false;
  private wasOpen = false;
  private reconnecting = false;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly items = computed<FamilyAlert[]>(() => {
    const byAlert = new Map<string, Decision[]>();
    for (const d of this.decisions().values()) {
      byAlert.set(d.alertId, [...(byAlert.get(d.alertId) ?? []), d]);
    }
    return [...this.alerts().values()]
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
      .map((alert) => ({
        alert,
        decisions: (byAlert.get(alert.alertId) ?? []).sort((a, b) => Date.parse(a.at) - Date.parse(b.at)),
      }));
  });

  readonly needsAttention = computed(() => {
    const decidedByFamily = new Set(
      [...this.decisions().values()].filter((d) => d.actor === DecisionActorEnum.family).map((d) => d.alertId),
    );
    return [...this.attention()].some((id) => !decidedByFamily.has(id));
  });

  constructor() {
    this.load();
    effect(() => this.addAlerts(this.events.alerts(), true));
    effect(() => {
      const list = this.events.decisions();
      untracked(() => this.addDecisions(list));
    });
    effect(() => {
      // Calls that ended while the socket was down are only in history (FF-16).
      const open = this.events.connection() === 'open';
      untracked(() => {
        if (!open && this.wasOpen) this.reconnecting = true;
        if (open && this.reconnecting) {
          this.reconnecting = false;
          this.load();
        }
        this.wasOpen = this.wasOpen || open;
      });
    });
    effect(() => this.title.setTitle(this.needsAttention() ? ALERT_TITLE : FAMILY_TITLE));
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAlerts(50).subscribe({
      next: (list) => {
        this.addAlerts(list.map((x) => x.alert), false);
        this.addDecisions(list.flatMap((x) => x.decisions));
        this.finishLoading();
      },
      error: (err: unknown) => {
        this.error.set(problemDetailText(err));
        this.finishLoading();
      },
    });
  }

  private finishLoading(): void {
    this.loaded = true;
    this.loading.set(false);
  }

  private addAlerts(list: Alert[], live: boolean): void {
    untracked(() => {
      const known = this.alerts();
      const fresh = list.filter((a) => !known.has(a.alertId));
      if (!list.length) return;
      this.alerts.update((map) => {
        const next = new Map(map);
        for (const a of list) next.set(a.alertId, a);
        return next;
      });
      // Only alerts that are new while the page is open ring; history and the first snapshot do not.
      const ring = live && this.loaded ? fresh.filter((a) => a.level === RiskLevel.high) : [];
      if (ring.length) {
        this.attention.update((set) => new Set([...set, ...ring.map((a) => a.alertId)]));
        this.notifier.beep();
      }
    });
  }

  private addDecisions(list: Decision[]): void {
    if (!list.length) return;
    this.decisions.update((map) => {
      const next = new Map(map);
      for (const d of list) next.set(decisionKey(d), d);
      return next;
    });
  }
}

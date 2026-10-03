import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuditService } from '../../api/api/audit.service';
import { CallsService } from '../../api/api/calls.service';
import { DemoService } from '../../api/api/demo.service';
import { AuditHit, AuditRecord, AuditSummary, CallSummary, Mode, ScenarioSummary, StageId } from '../../api/model/models';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { problemDetailText } from '../../shared/problem-detail';
import { RiskLevelChip } from '../../shared/risk-level-chip';
import { STAGE_LABELS } from '../../shared/stage-label.pipe';
import { SystemStatusBar } from '../../shared/system-status-bar';
import { formatClock } from '../../shared/time';
import { TranscriptDebugPanel, keywordHighlights } from '../../shared/transcript-debug-panel';

/** Stages the model found (validated) that the keyword layer did not find in the same segments: the value of AI. */
export function aiOnlyStages(record: AuditRecord): StageId[] {
  const byKeywords = new Set(record.keywordHits.map((h) => h.stage));
  const byAi = record.hits.filter((h) => h.source === 'llm' && h.validated).map((h) => h.stage);
  return [...new Set(byAi)].filter((s) => !byKeywords.has(s));
}

const SPEEDS = [1, 2, 4] as const;

/**
 * Audit screen (FE-07, architecture 6.8 and section 9): the demo scenario picker, the measured summary of real AI calls
 * (latency, cost, rejected quotes) and, per call, every AI call with tokens, latency, validation of each quote and the
 * keyword layer for the same segments. Jury evidence: what the AI found that keywords missed, and that every quote
 * was checked. Mock calls are labelled and not counted in the numbers.
 */
@Component({
  selector: 'app-audit',
  imports: [DecimalPipe, Icon, ModeBadge, RiskLevelChip, RouterLink, SystemStatusBar, TranscriptDebugPanel],
  template: `
    <header class="top">
      <div class="brand"><span class="logo"><app-icon name="shield" [size]="24" /></span><strong>Anioł Stróż</strong><span class="sub">Audyt</span></div>
      <nav class="nav" aria-label="Panel rodziny">
        <a routerLink="/family">Panel rodziny</a>
        <a routerLink="/setup">Ustawienia</a>
      </nav>
      <app-mode-badge />
    </header>
    <app-system-status-bar />

    <main>
      <section class="card" aria-labelledby="demo-title">
        <h2 id="demo-title">Scenariusz demo</h2>
        <p class="note">Odtwarza zapisaną rozmowę jako prawdziwą: tekst przechodzi przez AI, słowa kluczowe i reguły ryzyka. Każdy ekran pokazuje tryb.</p>
        <div class="row">
          <label for="scenario">Scenariusz</label>
          <select id="scenario" [value]="scenarioId()" (change)="scenarioId.set($any($event.target).value)">
            @for (s of scenarios(); track s.scenarioId) {
              <option [value]="s.scenarioId">{{ s.title }}</option>
            }
          </select>
          <label for="speed">Tempo</label>
          <select id="speed" [value]="speed()" (change)="speed.set(+$any($event.target).value)">
            @for (v of speeds; track v) {
              <option [value]="v">{{ v }}×</option>
            }
          </select>
        </div>
        @if (selectedScenario(); as s) {
          <p class="note">{{ s.description }}</p>
        }
        <div class="actions">
          <button type="button" class="primary" [disabled]="!scenarioId() || busy() || callInProgress()" (click)="play()">
            <app-icon name="play" [size]="20" /> Odtwórz (SCRIPTED)
          </button>
          @if (selectedScenario()?.hasRecording) {
            <button type="button" class="primary" [disabled]="busy() || callInProgress()" (click)="play('REPLAY')">
              <app-icon name="mic" [size]="20" /> Odtwórz nagranie (REPLAY)
            </button>
          }
          <button type="button" class="secondary" [disabled]="busy()" (click)="stop()">
            <app-icon name="pause" [size]="20" /> Zatrzymaj
          </button>
        </div>
        @if (demoError(); as error) {
          <p class="problem" role="alert"><app-icon name="warning" [size]="20" /> {{ error }}</p>
        }
      </section>

      <section class="card" aria-labelledby="summary-title">
        <h2 id="summary-title">Zmierzone liczby (tylko prawdziwe wywołania AI)</h2>
        @if (summary(); as s) {
          <dl class="stats">
            <div><dt>Wywołania AI</dt><dd>{{ s.aiCalls }}</dd></div>
            <div><dt>Rozmowy z AI</dt><dd>{{ s.conversations }}</dd></div>
            <div><dt>Opóźnienie p50</dt><dd>{{ s.latencyP50Ms != null ? (s.latencyP50Ms / 1000 | number: '1.1-1') + ' s' : '—' }}</dd></div>
            <div><dt>Opóźnienie p95</dt><dd>{{ s.latencyP95Ms != null ? (s.latencyP95Ms / 1000 | number: '1.1-1') + ' s' : '—' }}</dd></div>
            <div><dt>Koszt na wywołanie</dt><dd>{{ s.avgCostPerCallUsd != null ? '$' + (s.avgCostPerCallUsd | number: '1.4-4') : '—' }}</dd></div>
            <div><dt>Koszt na rozmowę</dt><dd>{{ s.avgCostPerConversationUsd != null ? '$' + (s.avgCostPerConversationUsd | number: '1.4-4') : '—' }}</dd></div>
            <div><dt>Odrzucone cytaty</dt><dd>{{ s.rejectedQuotes }}</dd></div>
            <div><dt>Spóźnione odpowiedzi</dt><dd>{{ s.lateResults }}</dd></div>
            <div><dt>Pominięte wywołania MOCK</dt><dd>{{ s.mockCallsExcluded }}</dd></div>
          </dl>
          @if (errorCauses().length) {
            <p class="note">Błędy: @for (e of errorCauses(); track e[0]) { <span class="tag">{{ e[0] }}: {{ e[1] }}</span> }</p>
          }
          <p class="note">{{ s.costNote }}</p>
        } @else if (summaryError(); as error) {
          <p class="problem" role="alert">Nie udało się wczytać podsumowania: {{ error }}</p>
        } @else {
          <p class="note" role="status">Wczytuję…</p>
        }
      </section>

      <section class="card" aria-labelledby="calls-title">
        <div class="row between">
          <h2 id="calls-title">Rozmowy</h2>
          <button type="button" class="secondary" (click)="refresh()">Odśwież</button>
        </div>
        @if (callsError(); as error) {
          <p class="problem" role="alert">Nie udało się wczytać rozmów: {{ error }}</p>
        }
        <div class="scroll">
          <table>
            <thead>
              <tr><th scope="col">Start</th><th scope="col">Tryb</th><th scope="col">Najwyższy poziom</th><th scope="col">Wywołania AI</th><th scope="col"></th></tr>
            </thead>
            <tbody>
              @for (c of calls(); track c.callId) {
                <tr [class.selected]="c.callId === selectedCall()">
                  <td>{{ clock(c.startedAt) }}{{ c.endedAt ? '' : ' (trwa)' }}</td>
                  <td><span class="tag">{{ c.mode }}</span></td>
                  <td><app-risk-level-chip [level]="c.maxLevel" /></td>
                  <td>{{ c.aiCalls }}</td>
                  <td><button type="button" class="link" (click)="open(c.callId)">Szczegóły</button></td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="note">Brak rozmów. Odtwórz scenariusz demo.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      @if (selectedCall()) {
        <section class="card" aria-labelledby="records-title">
          <h2 id="records-title">Wywołania AI w tej rozmowie</h2>
          @if (recordsError(); as error) {
            <p class="problem" role="alert">Nie udało się wczytać audytu: {{ error }}</p>
          }
          @for (r of records(); track r.id) {
            <article class="record">
              <header class="row">
                <strong>{{ r.segmentRange }}</strong>
                <span class="tag">{{ r.mode }}</span>
                <span class="tag">{{ r.model }}{{ r.effort ? ' · ' + r.effort : '' }}</span>
                <span>{{ r.latencyMs / 1000 | number: '1.2-2' }} s</span>
                <span>tokeny: {{ r.usage.inputTokens }} we · {{ r.usage.cacheReadInputTokens }} cache · {{ r.usage.cacheCreationInputTokens }} zapis · {{ r.usage.outputTokens }} wy</span>
                <span>{{ r.stopReason ?? '' }}</span>
                <span>poziom: <app-risk-level-chip [level]="r.levelBefore" /> → <app-risk-level-chip [level]="r.levelAfter" /></span>
              </header>
              @if (r.error; as e) {
                <p class="problem">Błąd: {{ e }}</p>
              }
              @if (r.late) {
                <p class="note">Odpowiedź przyszła po zakończeniu rozmowy, nie była sprawdzana.</p>
              }
              @if (aiOnly(r).length) {
                <p class="ai-only"><app-icon name="check" [size]="18" /> Tylko AI (słowa kluczowe nie znalazły): @for (s of aiOnly(r); track s) { <span class="tag">{{ label(s) }}</span> }</p>
              }
              <div class="hits">
                <div>
                  <h3>AI</h3>
                  <ul>
                    @for (h of r.hits; track $index) {
                      <li [class.rejected]="!h.validated && !r.late">
                        <span class="verdict" [attr.aria-label]="h.validated ? 'cytat sprawdzony' : 'cytat odrzucony'">{{ h.validated ? '✓' : '✗' }}</span>
                        <strong>{{ label(h.stage) }}</strong> <span class="seg">{{ h.segId }} · {{ role(h) }}</span>
                        @if (h.quote) { <q>{{ h.quote }}</q> }
                      </li>
                    } @empty {
                      <li class="note">Brak etapów.</li>
                    }
                  </ul>
                </div>
                <div>
                  <h3>Słowa kluczowe</h3>
                  <ul>
                    @for (h of r.keywordHits; track $index) {
                      <li><strong>{{ label(h.stage) }}</strong> <span class="seg">{{ h.segId }}</span> @if (h.quote) { <q>{{ h.quote }}</q> }</li>
                    } @empty {
                      <li class="note">Brak trafień.</li>
                    }
                  </ul>
                </div>
              </div>
              @if (r.textCleared) {
                <p class="note">Tekst usunięty: rozmowa bez ostrzeżenia (zostały same liczby).</p>
              }
            </article>
          } @empty {
            @if (!recordsError()) {
              <p class="note">Ta rozmowa nie miała wywołań AI.</p>
            }
          }
        </section>
      }

      <section class="card transcript" aria-label="Transkrypcja na żywo">
        <app-transcript-debug-panel [segments]="events.segments()" [highlights]="highlights()" />
      </section>
    </main>
  `,
  styles: `
    .top { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
      padding: 12px 16px; background: var(--surface); border-bottom: 1px solid var(--border); }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; }
    .sub { color: var(--muted); font-size: 15px; }
    .logo { display: inline-flex; padding: 6px; background: var(--primary); color: #fff; border-radius: 8px; }
    .nav { display: flex; gap: 16px; font-weight: 700; }
    .nav a { color: var(--primary); }
    main { max-width: 1100px; margin: 0 auto; padding: 16px; display: grid; gap: 16px; }
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 16px; display: grid; gap: 10px; }
    h2 { font-size: 18px; margin: 0; }
    h3 { font-size: 13px; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); margin: 0 0 6px; }
    .row { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; }
    .between { justify-content: space-between; }
    select { font: inherit; min-height: 40px; padding: 0 8px; border-radius: 8px; border: 1px solid var(--border); max-width: 100%; }
    .actions { display: flex; flex-wrap: wrap; gap: 10px; }
    button { font: inherit; font-weight: 700; min-height: 44px; padding: 0 16px; border-radius: 10px; border: 2px solid var(--primary);
      cursor: pointer; display: inline-flex; align-items: center; gap: 8px; }
    .primary { background: var(--primary); color: #fff; }
    .secondary { background: var(--surface); color: var(--primary); }
    .link { border: 0; background: none; color: var(--primary); min-height: 32px; padding: 0; text-decoration: underline; }
    button:disabled { opacity: 0.6; cursor: default; }
    button:focus-visible, select:focus-visible { outline: 3px solid var(--highlight); outline-offset: 2px; }
    .note { margin: 0; color: var(--muted); font-size: 14px; }
    .problem { margin: 0; padding: 8px 12px; border-radius: 8px; background: var(--warn-bg); color: var(--warn-deep); font-weight: 700; }
    .stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; margin: 0; }
    .stats div { background: var(--bg); border-radius: 8px; padding: 8px 10px; }
    dt { font-size: 13px; color: var(--muted); }
    dd { margin: 0; font-size: 20px; font-weight: 800; font-variant-numeric: tabular-nums; }
    .tag { display: inline-block; border: 1px solid var(--border); border-radius: 999px; padding: 1px 8px; font-size: 13px; margin: 0 4px 2px 0; }
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 15px; }
    th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--border); }
    tr.selected { background: var(--bg); }
    .record { border-top: 1px solid var(--border); padding-top: 10px; display: grid; gap: 8px; }
    .record header { font-size: 14px; }
    .hits { display: grid; gap: 12px; }
    @media (min-width: 800px) { .hits { grid-template-columns: 1fr 1fr; } }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    li { font-size: 14px; }
    li.rejected { text-decoration: line-through; color: var(--muted); }
    .verdict { font-weight: 800; margin-right: 4px; }
    .seg { color: var(--muted); }
    q { font-style: italic; quotes: '„' '”'; display: block; }
    .ai-only { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-weight: 700; color: var(--primary); }
    .transcript { max-height: 50vh; display: flex; flex-direction: column; }
  `,
})
export class Audit {
  protected readonly events = inject(EventsService);
  private readonly callsApi = inject(CallsService);
  private readonly auditApi = inject(AuditService);
  private readonly demo = inject(DemoService);

  protected readonly highlights = computed(() => keywordHighlights(this.events.alerts()));
  protected readonly speeds = SPEEDS;
  protected readonly clock = formatClock;
  protected readonly aiOnly = aiOnlyStages;

  protected readonly scenarios = signal<(ScenarioSummary & { hasRecording?: boolean })[]>([]);
  protected readonly scenarioId = signal('');
  protected readonly speed = signal<number>(1);
  protected readonly selectedScenario = computed(() => this.scenarios().find((s) => s.scenarioId === this.scenarioId()) ?? null);
  protected readonly busy = signal(false);
  protected readonly demoError = signal<string | null>(null);
  protected readonly callInProgress = computed(() => {
    const call = this.events.activeCall();
    return !!call && !call.endedAt;
  });

  protected readonly summary = signal<AuditSummary | null>(null);
  protected readonly summaryError = signal<string | null>(null);
  protected readonly errorCauses = computed(() => Object.entries(this.summary()?.errorsByCause ?? {}));
  protected readonly calls = signal<CallSummary[]>([]);
  protected readonly callsError = signal<string | null>(null);
  protected readonly selectedCall = signal<string | null>(null);
  protected readonly records = signal<AuditRecord[]>([]);
  protected readonly recordsError = signal<string | null>(null);

  constructor() {
    void this.loadScenarios();
    void this.refresh();
    // A call that just ended has its last AI calls in the audit: refresh the numbers and the open call.
    effect(() => {
      const call = this.events.activeCall();
      if (call?.endedAt) {
        untracked(() => void this.refresh());
      }
    });
  }

  protected label(stage: StageId): string {
    return STAGE_LABELS[stage] ?? stage;
  }

  protected role(hit: AuditHit): string {
    return { caller: 'rozmówca', senior: 'senior', background: 'tło', unclear: 'nie wiadomo' }[hit.speakerRole] ?? hit.speakerRole;
  }

  protected async refresh(): Promise<void> {
    const [summary, calls] = await Promise.allSettled([
      firstValueFrom(this.auditApi.getAuditSummary()),
      firstValueFrom(this.callsApi.listCalls(50)),
    ]);
    if (summary.status === 'fulfilled') {
      this.summary.set(summary.value);
      this.summaryError.set(null);
    } else {
      this.summaryError.set(problemDetailText(summary.reason));
    }
    if (calls.status === 'fulfilled') {
      this.calls.set(calls.value);
      this.callsError.set(null);
    } else {
      this.callsError.set(problemDetailText(calls.reason));
    }
    const open = this.selectedCall();
    if (open) {
      await this.open(open);
    }
  }

  protected async open(callId: string): Promise<void> {
    this.selectedCall.set(callId);
    this.recordsError.set(null);
    try {
      this.records.set(await firstValueFrom(this.callsApi.getCallAudit(callId)));
    } catch (err) {
      this.records.set([]);
      this.recordsError.set(problemDetailText(err));
    }
  }

  protected async play(mode: 'SCRIPTED' | 'REPLAY' = 'SCRIPTED'): Promise<void> {
    this.busy.set(true);
    this.demoError.set(null);
    try {
      await firstValueFrom(this.demo.startReplay({ scenarioId: this.scenarioId(), mode: mode as Mode, speed: this.speed() }));
    } catch (err) {
      this.demoError.set(`Nie udało się odtworzyć scenariusza: ${problemDetailText(err)}`);
    } finally {
      this.busy.set(false);
    }
  }

  protected async stop(): Promise<void> {
    this.busy.set(true);
    this.demoError.set(null);
    try {
      await firstValueFrom(this.demo.stopReplay());
    } catch (err) {
      this.demoError.set(`Nie udało się zatrzymać: ${problemDetailText(err)}`);
    } finally {
      this.busy.set(false);
    }
  }

  private async loadScenarios(): Promise<void> {
    try {
      const list = await firstValueFrom(this.demo.listScenarios());
      this.scenarios.set(list);
      if (!this.scenarioId() && list.length) {
        this.scenarioId.set(list[0].scenarioId);
      }
    } catch (err) {
      this.demoError.set(`Nie udało się wczytać scenariuszy: ${problemDetailText(err)}`);
    }
  }
}

import { Component, computed, inject } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { StageId } from '../../core/contracts';
import { createResource } from '../../core/resource';
import { FamilyDashboard, STAGE_LABELS, SCRIPT_STAGES, Settings, StageEvidence, formatDuration } from '../../core/view-models';
import { ConnectionStatusComponent } from '../../shared/connection-status.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { IconComponent } from '../../shared/icon.component';
import { LoadingComponent } from '../../shared/loading.component';
import { ModeBadgeComponent } from '../../shared/mode-badge.component';
import { RiskLevelChipComponent } from '../../shared/risk-level-chip.component';

interface TranscriptRow {
  segId: string;
  time: string;
  speaker: string;
  isCaller: boolean;
  before: string;
  quote: string;
  after: string;
  stageTag: string | null;
}

const SOURCE_LABEL = { llm: 'AI', keywords: 'słowa kluczowe', both: 'AI + słowa kluczowe' } as const;

/** Family panel. Mobile first, two columns from 900 px (WEB-12). Level and stages come from the backend (FE-04). */
@Component({
  selector: 'app-family',
  imports: [ConnectionStatusComponent, ErrorBannerComponent, IconComponent, LoadingComponent, ModeBadgeComponent, RiskLevelChipComponent],
  template: `
    <header class="top">
      <div class="brand">
        <span class="logo"><app-icon name="shield" [size]="24" /></span>
        <strong>Anioł Stróż</strong>
        <span class="sep"></span>
        <span class="sub">Panel rodziny</span>
      </div>
      <div class="right">
        <app-mode-badge />
        <app-connection-status
          [tone]="d()?.connected ? 'ok' : 'error'"
          [label]="d()?.connected ? 'Wszystkie urządzenia połączone' : 'Brak połączenia'" />
        <span class="chip">{{ settings()?.senior?.displayName }}</span>
      </div>
    </header>

    <div class="wrap">
      @if (res.loading()) {
        <app-loading label="Ładowanie…" />
      }
      @if (res.error(); as err) {
        <app-error-banner [message]="err" />
      }

      @if (d(); as dash) {
        <section class="alarm" role="alert">
          <app-icon name="warning" [size]="36" />
          <div class="alarm-text"><strong>{{ dash.alertTitle }}</strong><span>{{ dash.alertText }}</span></div>
          <span class="time">{{ dash.alertTime }}</span>
        </section>

        <section class="card call">
          <div><span>Numer dzwoniącego</span><strong>{{ dash.call.callerNumberMasked }}</strong></div>
          <div>
            <span>Kontakty</span>
            <strong>@if (dash.call.isKnownContact) { <span class="tag ok">W kontaktach</span> } @else { <span class="tag">Spoza kontaktów</span> }</strong>
          </div>
          <div><span>Początek</span><strong>{{ dash.call.startedAt }}</strong></div>
          <div><span>Czas rozmowy</span><strong>{{ duration() }}</strong></div>
        </section>

        <div class="cols">
          <section class="card transcript">
            <div class="t-head"><h2>Transkrypcja na żywo</h2><span class="live"><span class="dot"></span> NA ŻYWO</span></div>
            @for (row of rows(); track row.segId) {
              <div class="row">
                <span class="t">{{ row.time }}</span>
                <div>
                  <div class="who" [class.caller]="row.isCaller">{{ row.speaker }}</div>
                  <p>{{ row.before }}@if (row.quote) {<mark>{{ row.quote }}</mark>}{{ row.after }}</p>
                  @if (row.stageTag) { <span class="stage-tag">{{ row.stageTag }}</span> }
                </div>
              </div>
            }
          </section>

          <aside>
            <section class="card">
              <h2>Poziom ryzyka</h2>
              <div class="risk"><app-risk-level-chip [level]="dash.level" /></div>
            </section>

            <section class="card">
              <h2>Etapy legendy „na policjanta”</h2>
              <ol class="stages">
                @for (s of stageList(); track s.stage; let i = $index) {
                  <li>
                    <span class="num" [class.done]="s.evidence">{{ i + 1 }}</span>
                    <div>
                      <strong [class.dim]="!s.evidence">{{ s.label }}</strong>
                      @if (s.evidence; as ev) {
                        <span class="detail">„{{ ev.hit.quote }}” · {{ ev.time }} · {{ ev.source }}</span>
                      } @else {
                        <span class="detail">Jeszcze nie wykryto</span>
                      }
                    </div>
                  </li>
                }
              </ol>
            </section>

            <section class="card actions">
              <h2>Co możesz zrobić</h2>
              <!-- TODO: no endpoint exists for sending a warning to the senior (API-01); UI only. -->
              <button type="button" class="btn-primary btn-block" (click)="warningSent = true">
                <app-icon name="send" [size]="22" /> Wyślij mamie ostrzeżenie
              </button>
              @if (warningSent) { <p class="note" role="status">Funkcja będzie dostępna po podłączeniu serwera.</p> }
              <a class="btn-secondary btn-block" [href]="seniorTel()"><app-icon name="phone" [size]="22" /> Zadzwoń do mamy</a>
              <!-- TODO: POST /api/alerts/:id/decision (false_alarm) once the endpoint exists. -->
              <button type="button" class="btn-neutral btn-block">Fałszywy alarm</button>
              <p class="note">Twoja ocena trafia do historii alarmów i pomaga ograniczać fałszywe ostrzeżenia.</p>
            </section>
          </aside>
        </div>

        <p class="privacy"><app-icon name="lock" [size]="18" /> Nagrania audio nie są przechowywane. Transkrypcja służy wyłącznie do oceny ryzyka.</p>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-height: 100dvh; }
    .top { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px;
      padding: 12px 16px; background: var(--surface); border-bottom: 1px solid var(--border); }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; }
    .logo { display: inline-flex; padding: 6px; background: var(--primary); color: #fff; border-radius: 8px; }
    .sep { width: 1px; height: 24px; background: var(--border); }
    .sub { color: var(--muted); font-size: 16px; }
    .right { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; font-size: 14px; }
    .chip { border: 1px solid var(--border); border-radius: 8px; padding: 6px 14px; font-weight: 600; background: var(--surface); }
    .wrap { max-width: 1200px; margin: 0 auto; padding: 16px; display: grid; gap: 16px; }
    .alarm { display: flex; align-items: center; gap: 14px; background: var(--alarm-deep); color: #fff; border-radius: 12px; padding: 16px; }
    .alarm-text { flex: 1; display: grid; }
    .alarm-text strong { font-size: 18px; }
    .time { border: 1px solid #fff; border-radius: 6px; padding: 4px 10px; font-weight: 700; }
    .call { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .call div { display: grid; gap: 2px; }
    .call span { color: var(--muted); font-size: 14px; }
    .call strong { font-size: 20px; }
    .tag { display: inline-block; background: var(--highlight); color: #4a3500; padding: 2px 10px; border-radius: 6px; font-size: 15px; font-weight: 700; }
    .tag.ok { background: var(--safe-bg); color: var(--safe); }
    .cols { display: grid; gap: 16px; align-items: start; }
    aside { display: grid; gap: 16px; }
    .t-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .t-head h2 { margin: 0; }
    .live { color: var(--alarm); font-weight: 700; font-size: 14px; display: inline-flex; align-items: center; gap: 6px; }
    .dot { width: 9px; height: 9px; border-radius: 50%; background: var(--alarm); }
    .row { display: grid; grid-template-columns: 52px 1fr; gap: 8px; padding: 10px 0; border-top: 1px solid var(--bg); }
    .row .t { color: var(--muted); font-variant-numeric: tabular-nums; }
    .row p { margin: 2px 0 4px; font-size: 17px; }
    .who { font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
    .who.caller { color: var(--navy); }
    mark { background: var(--highlight); color: var(--text); padding: 1px 3px; border-radius: 3px; }
    .stage-tag { display: inline-block; background: var(--highlight); color: #4a3500; font-size: 13px; font-weight: 600; padding: 1px 8px; border-radius: 4px; }
    .risk { padding: 4px 0 8px; font-size: 24px; }
    .stages { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
    .stages li { display: flex; gap: 12px; }
    .stages li > div { display: grid; }
    .num { flex: none; width: 28px; height: 28px; border-radius: 50%; border: 2px solid var(--border); color: var(--muted);
      display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; }
    .num.done { background: var(--alarm); border-color: var(--alarm); color: #fff; }
    .dim { color: var(--muted); }
    .detail { color: var(--muted); font-size: 14px; }
    .actions { display: grid; gap: 10px; }
    .actions h2 { margin-bottom: 2px; }
    .note { margin: 0; color: var(--muted); font-size: 14px; }
    .privacy { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 14px; margin: 8px 0 0; }
    @media (min-width: 900px) {
      .call { grid-template-columns: repeat(4, 1fr); }
      .cols { grid-template-columns: minmax(0, 1fr) 400px; }
    }
  `,
})
export class FamilyComponent {
  private readonly api = inject(ApiService);
  protected readonly res = createResource<FamilyDashboard>();
  private readonly settingsRes = createResource<Settings>();
  protected readonly d = this.res.data;
  protected readonly settings = this.settingsRes.data;
  protected warningSent = false;

  protected readonly duration = computed(() => formatDuration(this.d()?.call.durationSeconds ?? 0));
  // FE-10: the number comes from settings only.
  protected readonly seniorTel = computed(() => `tel:${this.settings()?.senior.phone ?? ''}`);

  protected readonly rows = computed<TranscriptRow[]>(() => {
    const dash = this.d();
    if (!dash) return [];
    return dash.segments.map((seg) => {
      const ev = dash.stages.find((s) => s.hit.segId === seg.segId);
      const quote = ev && seg.text.includes(ev.hit.quote) ? ev.hit.quote : '';
      const at = quote ? seg.text.indexOf(quote) : 0;
      const isCaller = seg.speaker === 'A';
      return {
        segId: seg.segId,
        time: formatDuration(Math.floor(seg.tStartMs / 1000)),
        speaker: isCaller ? 'Rozmówca' : 'Mama',
        isCaller,
        before: quote ? seg.text.slice(0, at) : seg.text,
        quote,
        after: quote ? seg.text.slice(at + quote.length) : '',
        stageTag: ev ? `Etap ${this.stageNumber(ev.hit.stage)} · ${STAGE_LABELS[ev.hit.stage].toLowerCase()}` : null,
      };
    });
  });

  protected readonly stageList = computed(() => {
    const evidence = this.d()?.stages ?? [];
    return SCRIPT_STAGES.map((stage) => {
      const ev: StageEvidence | undefined = evidence.find((e) => e.hit.stage === stage);
      return {
        stage,
        label: STAGE_LABELS[stage],
        evidence: ev ? { hit: ev.hit, time: formatDuration(Math.floor(ev.tMs / 1000)), source: SOURCE_LABEL[ev.detectedBy] } : null,
      };
    });
  });

  constructor() {
    this.res.load(this.api.familyDashboard());
    this.settingsRes.load(this.api.settings());
  }

  private stageNumber(stage: StageId): number {
    return SCRIPT_STAGES.indexOf(stage) + 1;
  }
}

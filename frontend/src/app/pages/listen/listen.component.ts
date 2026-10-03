import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/api.service';
import { createResource } from '../../core/resource';
import { ListenState, formatDuration } from '../../core/view-models';
import { RISK_LEVEL_WORDS } from '../../shared/risk-level-chip.component';
import { ConnectionStatusComponent, ConnectionTone } from '../../shared/connection-status.component';
import { IconComponent } from '../../shared/icon.component';
import { ModeBadgeComponent } from '../../shared/mode-badge.component';

type ListenMode = 'idle' | 'listening' | 'alarm' | 'offline' | 'audio_lost';

const MODES: { id: ListenMode; label: string }[] = [
  { id: 'idle', label: 'Start' },
  { id: 'listening', label: 'Słucham' },
  { id: 'alarm', label: 'Alarm' },
  { id: 'offline', label: 'Offline' },
  { id: 'audio_lost', label: 'Brak dźwięku' },
];

/** "Nasłuch": navy full-screen front for the tablet next to the landline. Mock data only, no microphone. */
@Component({
  selector: 'app-listen',
  imports: [ConnectionStatusComponent, IconComponent, ModeBadgeComponent],
  template: `
    <div class="screen" [class]="mode()">
      <header>
        <div class="brand">
          <span class="logo"><app-icon name="shield" [size]="28" /></span>
          <strong>Nasłuch</strong>
        </div>
        <div class="head-right">
          <app-mode-badge [dark]="true" />
          <app-connection-status [tone]="tone()" [label]="connectionLabel()" />
        </div>
      </header>

      <main aria-live="polite">
        @switch (mode()) {
          @case ('idle') {
            <div class="mic"><app-icon name="mic" [size]="72" /></div>
            <h1>Ochrona jest wyłączona</h1>
            <p class="sub">Włącz ochronę, aby urządzenie zaczęło słuchać rozmowy przy telefonie.</p>
            <button type="button" class="start" (click)="start()">Włącz ochronę</button>
          }
          @case ('listening') {
            <div class="mic rings"><app-icon name="mic" [size]="72" /></div>
            <h1>Słucham rozmowy</h1>
            <p class="sub">{{ callSubtitle() }}</p>
            <div class="bars" aria-hidden="true">
              @for (b of bars; track $index) {
                <span [style.animation-delay.ms]="$index * 60" [style.--h.px]="b"></span>
              }
            </div>
            <div class="cards">
              <div class="info"><span>Numer</span><strong>{{ state()?.call?.callerNumberMasked }}</strong></div>
              <div class="info"><span>Czas</span><strong>{{ time() }}</strong></div>
            </div>
          }
          @case ('alarm') {
            <div class="mic alarm-icon"><app-icon name="warning" [size]="72" /></div>
            <p class="eyebrow">ALARM</p>
            <h1 role="alert">{{ state()?.alertTitle }}</h1>
            <p class="sub">{{ state()?.alertReason }}</p>
            <blockquote>„{{ state()?.quote }}”</blockquote>
            <ul class="checks">
              <li><span class="tick"><app-icon name="check" [size]="20" /></span> Senior otrzymał ostrzeżenie</li>
              <li><span class="tick"><app-icon name="check" [size]="20" /></span> Rodzina została powiadomiona</li>
            </ul>
            <p class="risk">Poziom ryzyka <strong>{{ riskWord() }}</strong></p>
          }
          @case ('offline') {
            <div class="mic alarm-icon"><app-icon name="warning" [size]="72" /></div>
            <h1 role="alert">Anioł Stróż jest offline</h1>
            <p class="sub">Brak połączenia z serwerem. Rozmowa nie jest teraz sprawdzana.</p>
          }
          @case ('audio_lost') {
            <div class="mic warn-icon"><app-icon name="mic" [size]="72" /></div>
            <h1 role="alert">Nie słyszę rozmowy</h1>
            <p class="sub">Sprawdź, czy mikrofon jest włączony i ustawiony blisko telefonu.</p>
          }
        }
      </main>

      <footer>
        <p><app-icon name="lock" [size]="22" /> Dźwięk nie jest nagrywany ani zapisywany</p>
        <p><app-icon name="monitor" [size]="22" /> Ekran pozostaje włączony</p>
      </footer>
    </div>

    @if (previewing) {
      <div class="state-switcher" role="group" aria-label="Podgląd stanów (tylko dane demo)">
        <span>Stan:</span>
        @for (m of modes; track m.id) {
          <button type="button" [class.on]="mode() === m.id" (click)="mode.set(m.id)">{{ m.label }}</button>
        }
      </div>
    }
  `,
  styles: `
    .screen { min-height: 100dvh; display: flex; flex-direction: column; background: var(--navy); color: #fff; }
    .screen.alarm, .screen.offline { background: var(--alarm-deep); }
    .screen.audio_lost { background: var(--warn-deep); }
    header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px;
      padding: 16px 24px; border-bottom: 1px solid rgba(255, 255, 255, 0.18); }
    .brand { display: flex; align-items: center; gap: 14px; font-size: 22px; }
    .logo { display: inline-flex; padding: 6px; background: #fff; color: var(--primary); border-radius: 12px; }
    .head-right { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; }
    main { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
      text-align: center; padding: 24px; max-width: 640px; width: 100%; margin: 0 auto; }
    .mic { width: 172px; height: 172px; border-radius: 50%; background: #fff; color: var(--primary);
      display: flex; align-items: center; justify-content: center; margin-bottom: 28px; }
    .mic.rings { box-shadow: 0 0 0 20px rgba(255, 255, 255, 0.1), 0 0 0 40px rgba(255, 255, 255, 0.06); animation: pulse 2.4s ease-in-out infinite; }
    .mic.alarm-icon { color: var(--alarm); }
    .mic.warn-icon { color: var(--warn-deep); }
    @keyframes pulse { 50% { box-shadow: 0 0 0 28px rgba(255, 255, 255, 0.08), 0 0 0 54px rgba(255, 255, 255, 0.03); } }
    h1 { font-size: 36px; line-height: 1.15; font-weight: 800; margin: 0 0 8px; }
    .sub { font-size: 20px; opacity: 0.88; margin-bottom: 24px; }
    .eyebrow { letter-spacing: 0.15em; font-weight: 700; margin-bottom: 8px; }
    .start { font: inherit; font-size: 24px; font-weight: 800; min-height: 72px; padding: 0 48px; border-radius: 14px;
      border: 0; background: #fff; color: var(--primary); cursor: pointer; }
    .start:focus-visible { outline: 4px solid var(--highlight); outline-offset: 3px; }
    .bars { display: flex; align-items: center; gap: 6px; height: 64px; margin-bottom: 28px; }
    .bars span { width: 8px; height: var(--h, 20px); border-radius: 4px; background: #9ec0ff;
      animation: bar 1.1s ease-in-out infinite; transform-origin: center; }
    @keyframes bar { 50% { transform: scaleY(0.4); } }
    .cards { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; width: 100%; }
    .info { background: rgba(255, 255, 255, 0.1); border-radius: 14px; padding: 16px; text-align: left; display: grid; gap: 4px; }
    .info span { opacity: 0.85; }
    .info strong { font-size: 22px; }
    blockquote { margin: 0 0 24px; padding: 20px; width: 100%; background: rgba(0, 0, 0, 0.22); border-radius: 14px;
      font-size: 22px; font-style: italic; text-align: left; }
    .checks { list-style: none; padding: 0; margin: 0 0 16px; display: grid; gap: 12px; text-align: left; font-size: 22px; font-weight: 700; }
    .checks li { display: flex; align-items: center; gap: 12px; }
    .tick { display: inline-flex; padding: 6px; border-radius: 50%; background: #fff; color: var(--alarm-deep); }
    .risk { font-size: 20px; margin: 0; } .risk strong { font-size: 28px; margin-left: 8px; }
    footer { padding: 16px 24px 24px; display: grid; gap: 8px; max-width: 640px; width: 100%; margin: 0 auto; }
    footer p { margin: 0; display: flex; align-items: center; gap: 12px; opacity: 0.9; }
    @media (max-width: 480px) { h1 { font-size: 30px; } header { padding: 12px 16px; } }
  `,
})
export class ListenComponent {
  private readonly api = inject(ApiService);
  protected readonly previewing = environment.useMocks;
  protected readonly modes = MODES;
  protected readonly bars = [14, 26, 40, 22, 52, 64, 34, 18, 30, 56, 64, 38, 20, 14, 30, 48, 60, 36, 20, 28];

  protected readonly mode = signal<ListenMode>('idle');
  private readonly data = createResource<ListenState>();
  protected readonly state = this.data.data;
  private readonly seconds = signal(0);

  protected readonly time = computed(() => formatDuration(this.seconds()));
  protected readonly riskWord = computed(() => RISK_LEVEL_WORDS[this.state()?.level ?? 'none']);
  protected readonly callSubtitle = computed(() =>
    this.state()?.call.isKnownContact ? 'Połączenie z numeru z kontaktów' : 'Połączenie z numeru spoza kontaktów',
  );
  protected readonly tone = computed<ConnectionTone>(() => {
    switch (this.mode()) {
      case 'offline': return 'error';
      case 'audio_lost': return 'warn';
      default: return 'ok';
    }
  });
  protected readonly connectionLabel = computed(() => {
    switch (this.mode()) {
      case 'offline': return 'Brak połączenia z serwerem';
      case 'audio_lost': return 'Brak dźwięku';
      default: return 'Połączono z serwerem';
    }
  });

  constructor() {
    this.data.load(this.api.listenState());
    const timer = setInterval(() => {
      if (this.mode() === 'listening' || this.mode() === 'alarm') this.seconds.update((s) => s + 1);
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    // Start the clock at the mocked call duration once it is loaded.
    effect(() => {
      const s = this.state();
      if (s) this.seconds.set(s.call.durationSeconds);
    });
  }

  protected start(): void {
    // WEB-07: must run from a user gesture (this click).
    // TODO: request the microphone here (getUserMedia + AudioWorklet, WEB-08) once audio capture is implemented.
    this.mode.set('listening');
  }
}

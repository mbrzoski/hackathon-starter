import { Component, computed, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/api.service';
import { createResource } from '../../core/resource';
import { Settings, SeniorState } from '../../core/view-models';
import { IconComponent } from '../../shared/icon.component';
import { ModeBadgeComponent } from '../../shared/mode-badge.component';
import { ConnectionStatusComponent } from '../../shared/connection-status.component';

type SeniorMode = 'calm' | 'warning' | 'message';

const MODES: { id: SeniorMode; label: string }[] = [
  { id: 'calm', label: 'Spokój' },
  { id: 'warning', label: 'Ostrzeżenie' },
  { id: 'message', label: 'Wiadomość' },
];

/**
 * Senior app (later wrapped in Capacitor). Text >= 28 px, buttons >= 64 px (decisions 72 px), WEB-11.
 * Numbers come only from settings (FE-10); `tel:` opens only after a tap (FE-09).
 */
@Component({
  selector: 'app-senior',
  imports: [IconComponent, ModeBadgeComponent, ConnectionStatusComponent],
  template: `
    @switch (mode()) {
      @case ('calm') {
        <div class="page">
          <header>
            <div class="brand"><span class="logo"><app-icon name="shield" [size]="32" /></span> Anioł Stróż</div>
            <span class="help" aria-label="Pomoc"><app-icon name="help" [size]="32" /></span>
          </header>
          <div class="meta"><app-mode-badge /></div>
          <main>
            <section class="status safe">
              <span class="round"><app-icon name="check" [size]="44" /></span>
              <h1>Ochrona jest włączona</h1>
              <p>Sprawdzam rozmowy z numerów, których nie masz w kontaktach.</p>
            </section>
            <h2 class="label">Twoja rodzina</h2>
            <div class="contact">
              <span class="avatar">{{ contactName().charAt(0) }}</span>
              <div class="who"><strong>{{ contactName() }}</strong><span>{{ contactRelation() }} · czuwa nad Tobą</span></div>
              <app-connection-status tone="ok" label="Połączona" />
            </div>
            <div class="remember"><strong>Pamiętaj</strong><p>Policjant nigdy nie prosi o pieniądze ani o zachowanie rozmowy w tajemnicy przed rodziną.</p></div>
          </main>
          <footer>
            <a class="btn-primary decision" [href]="familyTel()"><app-icon name="phone" [size]="32" /> Zadzwoń do {{ contactDative() }}</a>
          </footer>
        </div>
      }
      @case ('warning') {
        <div class="page alert" aria-live="assertive" role="alert">
          <section class="banner">
            <app-icon name="warning" [size]="56" />
            <p class="eyebrow">OSTRZEŻENIE</p>
            <h1>{{ state()?.alertHeadline }}</h1>
            <p>{{ state()?.alertReason }}</p>
          </section>
          <main>
            <h2>Co zrobić teraz</h2>
            <ol>
              @for (step of state()?.steps; track step.title; let i = $index) {
                <li><span class="num">{{ i + 1 }}</span><div><strong>{{ step.title }}</strong><p>{{ step.text }}</p></div></li>
              }
            </ol>
          </main>
          <footer>
            <a class="btn-primary decision" [href]="familyTel()"><app-icon name="phone" [size]="32" /> Zadzwoń do {{ contactDative() }}</a>
            <a class="btn-secondary decision" [href]="emergencyTel()">Zadzwoń na {{ settings()?.emergencyNumber }}</a>
            <!-- TODO: POST /api/alerts/:id/decision (false_alarm) once the endpoint exists. -->
            <button type="button" class="btn-neutral decision" (click)="mode.set('calm')">To fałszywy alarm</button>
          </footer>
        </div>
      }
      @case ('message') {
        <div class="page">
          <header class="msg-head">
            <span class="msg-title"><app-icon name="mail" [size]="32" /> Wiadomość od rodziny</span>
            <span class="time">{{ state()?.message?.receivedAt }}</span>
          </header>
          <div class="meta"><app-mode-badge /></div>
          <main>
            <h1>{{ state()?.message?.from }} pisze:</h1>
            @if (state()?.message?.hasPhoto) {
              <div class="photo"><app-icon name="image" [size]="48" /><span>[Zdjęcie od {{ contactDative() }}]</span></div>
            }
            <p class="bubble">{{ state()?.message?.text }}</p>
            <p class="sig">— {{ state()?.message?.from }}</p>
          </main>
          <footer>
            <a class="btn-primary decision" [href]="familyTel()"><app-icon name="phone" [size]="32" /> Zadzwoń do {{ contactDative() }}</a>
            <button type="button" class="btn-secondary decision" (click)="mode.set('calm')">Rozumiem</button>
          </footer>
        </div>
      }
    }

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
    :host { display: block; background: var(--surface); }
    .page { min-height: 100dvh; max-width: 640px; margin: 0 auto; display: flex; flex-direction: column;
      font-size: 28px; line-height: 1.3; color: var(--text); }
    .page p { margin: 0; }
    header { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-bottom: 1px solid var(--border); }
    .brand { display: flex; align-items: center; gap: 14px; font-weight: 800; }
    .logo { display: inline-flex; padding: 8px; background: var(--primary); color: #fff; border-radius: 12px; }
    .help { color: var(--primary); border: 1px solid var(--border); border-radius: 50%; padding: 10px; display: inline-flex; }
    .meta { padding: 4px 20px 0; }
    main { flex: 1; padding: 16px 20px; display: grid; gap: 16px; align-content: start; }
    footer { padding: 16px 20px 24px; display: grid; gap: 12px; }
    h1 { font-size: 36px; line-height: 1.15; font-weight: 800; margin: 0; }
    h2 { font-size: 28px; margin: 0; }
    .decision { min-height: 72px; font-size: 28px; border-radius: 14px; }
    .status { border: 3px solid var(--safe); background: var(--safe-bg); border-radius: 16px; padding: 20px; display: grid; gap: 12px; }
    .round { width: 72px; height: 72px; border-radius: 50%; background: var(--safe); color: #fff; display: flex; align-items: center; justify-content: center; }
    .label { color: var(--muted); font-size: 28px; }
    .contact { display: flex; align-items: center; gap: 16px; border: 1px solid var(--border); border-radius: 16px; padding: 14px; }
    .avatar { width: 64px; height: 64px; border-radius: 50%; background: #e6edf8; color: var(--primary); font-weight: 800;
      display: flex; align-items: center; justify-content: center; flex: none; }
    .who { flex: 1; display: grid; }
    .who span { color: var(--muted); font-size: 28px; }
    .contact app-connection-status { font-size: 28px; }
    .remember { background: var(--bg); border-radius: 16px; padding: 16px; }
    .alert .banner { background: var(--alarm-deep); color: #fff; padding: 16px 20px; display: grid; gap: 4px; }
    .eyebrow { letter-spacing: 0.12em; font-weight: 700; font-size: 28px; }
    ol { list-style: none; padding: 0; margin: 0; display: grid; gap: 12px; }
    li { display: flex; gap: 16px; align-items: flex-start; }
    li p { color: var(--muted); }
    .num { flex: none; width: 52px; height: 52px; border: 3px solid var(--text); border-radius: 50%; font-weight: 800;
      display: flex; align-items: center; justify-content: center; }
    .msg-head { color: var(--primary); font-weight: 800; }
    .msg-title { display: flex; align-items: center; gap: 12px; }
    .time { color: var(--muted); font-weight: 400; }
    .photo { background: #e6edf8; color: var(--primary); border-radius: 16px; min-height: 200px; font-weight: 700;
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
    .bubble { background: var(--bg); border-radius: 16px; padding: 18px; font-weight: 600; }
    .sig { color: var(--muted); }
    @media (max-width: 400px) { h1 { font-size: 32px; } }
  `,
})
export class SeniorComponent {
  private readonly api = inject(ApiService);
  protected readonly previewing = environment.useMocks;
  protected readonly modes = MODES;
  protected readonly mode = signal<SeniorMode>('calm');

  private readonly stateRes = createResource<SeniorState>();
  private readonly settingsRes = createResource<Settings>();
  protected readonly state = this.stateRes.data;
  protected readonly settings = this.settingsRes.data;

  private readonly contact = computed(() => this.settings()?.contacts[0]);
  protected readonly contactName = computed(() => this.contact()?.name ?? '');
  protected readonly contactRelation = computed(() => this.contact()?.relation ?? '');
  // Polish dative for the one mocked contact ("Ania" -> "Ani"); TODO: the backend should supply the call-button label.
  protected readonly contactDative = computed(() => this.contactName().replace(/a$/, 'i'));
  // FE-10: numbers only from settings, never from the call. FE-09: nothing dials until the person taps.
  protected readonly familyTel = computed(() => `tel:${this.contact()?.phone ?? ''}`);
  protected readonly emergencyTel = computed(() => `tel:${this.settings()?.emergencyNumber ?? '112'}`);

  constructor() {
    this.stateRes.load(this.api.seniorState());
    this.settingsRes.load(this.api.settings());
  }
}

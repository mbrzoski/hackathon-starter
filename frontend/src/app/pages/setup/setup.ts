import { Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProtectionService } from '../../api/api/protection.service';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon';
import { SettingsWizard } from './settings-wizard';
import { ModeBadge } from '../../shared/mode-badge';
import { SystemStatusBar } from '../../shared/system-status-bar';

/**
 * Admin portal (family build): the setup wizard (consents, contacts, sensitivity, retention; FE-06) and the protection
 * switch. Protection is always on; this is the only place that can turn it off, so the senior has nothing to press.
 */
@Component({
  selector: 'app-setup',
  imports: [Icon, ModeBadge, RouterLink, SettingsWizard, SystemStatusBar],
  template: `
    <header class="top">
      <div class="brand"><span class="logo"><app-icon name="shield" [size]="24" /></span><strong>Anioł Stróż</strong></div>
      <nav class="nav" aria-label="Panel rodziny">
        <a routerLink="/family">Panel rodziny</a>
        <a routerLink="/audit">Audyt</a>
      </nav>
      <app-mode-badge />
    </header>
    <app-system-status-bar />
    <main class="card">
      <h1>Ustawienia</h1>

      <section class="protection" aria-labelledby="protection-title">
        <h2 id="protection-title">Ochrona</h2>
        @switch (enabled()) {
          @case (true) {
            <p class="state on" role="status"><app-icon name="check" [size]="24" /> Ochrona jest włączona. Urządzenie przy telefonie słucha rozmów.</p>
          }
          @case (false) {
            <p class="state off" role="status"><app-icon name="mic-off" [size]="24" /> Ochrona jest wyłączona. Urządzenie nie słucha rozmów i nic nie ostrzeże seniora.</p>
          }
          @default {
            <p class="state" role="status">{{ loadFailed() ? 'Nie udało się sprawdzić ustawienia ochrony.' : 'Sprawdzam ustawienie…' }}</p>
          }
        }

        @if (failed()) {
          <p class="problem" role="alert"><app-icon name="warning" [size]="24" /> Nie udało się zmienić ustawienia. Spróbuj ponownie.</p>
        }

        @if (enabled() === true) {
          @if (confirming()) {
            <p class="confirm">Na pewno wyłączyć ochronę? Rozmowy przestaną być sprawdzane.</p>
            <div class="actions">
              <button type="button" class="danger" [disabled]="busy()" (click)="set(false)">Tak, wyłącz ochronę</button>
              <button type="button" class="secondary" [disabled]="busy()" (click)="confirming.set(false)">Anuluj</button>
            </div>
          } @else {
            <div class="actions">
              <button type="button" class="secondary" (click)="confirming.set(true)">Wyłącz ochronę</button>
            </div>
          }
        } @else if (enabled() === false) {
          <div class="actions">
            <button type="button" class="primary" [disabled]="busy()" (click)="set(true)">Włącz ochronę</button>
          </div>
        } @else if (loadFailed()) {
          <div class="actions">
            <button type="button" class="secondary" (click)="load()">Spróbuj ponownie</button>
          </div>
        }
      </section>

      <app-settings-wizard />
    </main>
  `,
  styles: `
    .top { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
      padding: 12px 16px; background: var(--surface); border-bottom: 1px solid var(--border); }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; }
    .logo { display: inline-flex; padding: 6px; background: var(--primary); color: #fff; border-radius: 8px; }
    main { max-width: 800px; margin: 16px auto; }
    h2 { font-size: 18px; margin: 16px 0 8px; }
    p { margin: 0 0 12px; }
    .nav { display: flex; gap: 16px; font-weight: 700; }
    .nav a { color: var(--primary); }
    .state { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 10px; background: var(--bg); font-weight: 700; }
    .state.off, .problem { background: var(--warn-bg); color: var(--warn-deep); border: 2px solid var(--warn); }
    .problem { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 10px; font-weight: 700; }
    .confirm { font-weight: 700; }
    .actions { display: flex; flex-wrap: wrap; gap: 12px; }
    button { font: inherit; font-weight: 700; min-height: 48px; padding: 0 22px; border-radius: 10px; border: 2px solid var(--primary); cursor: pointer; }
    .primary { background: var(--primary); color: #fff; }
    .secondary { background: var(--surface); color: var(--primary); }
    .danger { background: var(--warn-deep); border-color: var(--warn-deep); color: #fff; }
    button:disabled { opacity: 0.6; cursor: default; }
    button:focus-visible { outline: 3px solid var(--highlight); outline-offset: 2px; }
  `,
})
export class Setup {
  private readonly protection = inject(ProtectionService);

  /** null until the backend answered. */
  protected readonly enabled = signal<boolean | null>(null);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  protected readonly loadFailed = signal(false);
  /** Switching off asks once more: one wrong tap must not leave a senior unprotected. */
  protected readonly confirming = signal(false);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loadFailed.set(false);
    try {
      this.enabled.set((await firstValueFrom(this.protection.getProtection())).enabled);
    } catch {
      this.loadFailed.set(true);
    }
  }

  protected async set(enabled: boolean): Promise<void> {
    this.busy.set(true);
    this.failed.set(false);
    try {
      this.enabled.set((await firstValueFrom(this.protection.setProtection({ enabled }))).enabled);
      this.confirming.set(false);
    } catch {
      this.failed.set(true);
    } finally {
      this.busy.set(false);
    }
  }
}

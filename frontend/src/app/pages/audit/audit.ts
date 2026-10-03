import { Component } from '@angular/core';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { SystemStatusBar } from '../../shared/system-status-bar';

/** Placeholder: the route and its /ws/events role exist, the screen comes in its own task. */
@Component({
  selector: 'app-audit',
  imports: [Icon, ModeBadge, SystemStatusBar],
  template: `
    <header class="top">
      <div class="brand"><span class="logo"><app-icon name="shield" [size]="24" /></span><strong>Anioł Stróż</strong></div>
      <app-mode-badge />
    </header>
    <app-system-status-bar />
    <main class="card">
      <h1>Audyt</h1>
      <p>Tabela wywołań AI (model, opóźnienie, tokeny, walidacja, AI kontra słowa kluczowe). Ekran powstanie w zadaniu audytu (GET /api/calls/:id/audit).</p>
    </main>
  `,
  styles: `
    .top { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
      padding: 12px 16px; background: var(--surface); border-bottom: 1px solid var(--border); }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; }
    .logo { display: inline-flex; padding: 6px; background: var(--primary); color: #fff; border-radius: 8px; }
    main { max-width: 800px; margin: 16px auto; }
    p { color: var(--muted); margin: 0; }
  `,
})
export class Audit {}

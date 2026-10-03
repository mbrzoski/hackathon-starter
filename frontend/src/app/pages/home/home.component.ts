import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Health } from '../../core/models';
import { createResource } from '../../core/resource';
import { CardComponent } from '../../shared/card.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { LoadingComponent } from '../../shared/loading.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, CardComponent, LoadingComponent, ErrorBannerComponent, EmptyStateComponent],
  template: `
    <h1>Dashboard</h1>
    @if (health.loading()) {
      <app-loading label="Checking backend…" />
    } @else if (health.error()) {
      <app-error-banner [message]="health.error()!" [retryable]="true" (retry)="refresh()" />
    } @else if (health.data()) {
      @let h = health.data()!;
      <div class="grid">
        <app-card title="Backend">
          <p class="big" [class.ok]="h.status === 'UP'">{{ h.status }}</p>
          <p>{{ h.app }} · profiles: {{ h.profiles.length ? h.profiles.join(', ') : 'default' }}</p>
        </app-card>
        <app-card title="Database">
          <p class="big" [class.ok]="h.database === 'UP'">{{ h.database }}</p>
          <p>PostgreSQL</p>
        </app-card>
        <app-card title="LLM">
          <p class="big" [class.ok]="h.llm.apiKeyConfigured">{{ h.llm.provider }}</p>
          <p>{{ h.llm.model }} · {{ h.llm.apiKeyConfigured ? 'ready' : 'API key missing' }}</p>
        </app-card>
      </div>
      <p><a routerLink="/playground">Open the LLM playground →</a></p>
    } @else {
      <app-empty-state message="No data yet." />
    }
  `,
})
export class HomeComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly health = createResource<Health>();

  ngOnInit(): void {
    this.refresh();
  }

  protected refresh(): void {
    this.health.load(this.api.health());
  }
}

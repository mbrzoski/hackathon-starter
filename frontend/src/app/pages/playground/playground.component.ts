import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ChatResponse, StructuredResponse, ToolChatResult } from '../../core/models';
import { createResource } from '../../core/resource';
import { CardComponent } from '../../shared/card.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ErrorBannerComponent } from '../../shared/error-banner.component';
import { LoadingComponent } from '../../shared/loading.component';

const EXAMPLE_SCHEMA = `{
  "type": "object",
  "required": ["title", "tags"],
  "properties": {
    "title": { "type": "string" },
    "tags": { "type": "array", "items": { "type": "string" } }
  }
}`;

/** Generic demo of the three backend building blocks. Replace with real screens; keep the patterns. */
@Component({
  selector: 'app-playground',
  imports: [FormsModule, CardComponent, LoadingComponent, ErrorBannerComponent, EmptyStateComponent],
  template: `
    <h1>LLM playground</h1>

    <app-card title="Chat">
      <textarea [(ngModel)]="chatMessage" rows="2" placeholder="Say something"></textarea>
      <button type="button" (click)="sendChat()" [disabled]="!chatMessage.trim() || chat.loading()">Send</button>
      @if (chat.loading()) {
        <app-loading />
      } @else if (chat.error()) {
        <app-error-banner [message]="chat.error()!" />
      } @else if (chat.data()) {
        @let r = chat.data()!;
        <pre>{{ r.text }}</pre>
        <small>{{ r.model }} · {{ r.usage.inputTokens }} in / {{ r.usage.outputTokens }} out</small>
      } @else {
        <app-empty-state message="No response yet." />
      }
    </app-card>

    <app-card title="Tool calling">
      <textarea [(ngModel)]="toolMessage" rows="2" placeholder="Ask something a tool can answer"></textarea>
      <button type="button" (click)="sendToolChat()" [disabled]="!toolMessage.trim() || tool.loading()">Run</button>
      @if (tool.loading()) {
        <app-loading />
      } @else if (tool.error()) {
        <app-error-banner [message]="tool.error()!" />
      } @else if (tool.data()) {
        @let r = tool.data()!;
        <pre>{{ r.text }}</pre>
        @for (step of r.steps; track $index) {
          <small>{{ step.error ? '✗' : '✓' }} {{ step.tool }} → {{ step.output }}</small><br />
        }
        @if (r.truncated) {
          <small>Stopped at the iteration limit.</small>
        }
      } @else {
        <app-empty-state message="No tool run yet." />
      }
    </app-card>

    <app-card title="Structured output">
      <textarea [(ngModel)]="structuredPrompt" rows="2" placeholder="Describe what to generate"></textarea>
      <textarea [(ngModel)]="schemaText" rows="8" spellcheck="false"></textarea>
      @if (schemaError()) {
        <app-error-banner [message]="schemaError()!" />
      }
      <button type="button" (click)="sendStructured()" [disabled]="!structuredPrompt.trim() || structured.loading()">
        Generate
      </button>
      @if (structured.loading()) {
        <app-loading />
      } @else if (structured.error()) {
        <app-error-banner [message]="structured.error()!" />
      } @else if (structured.data()) {
        <pre>{{ pretty(structured.data()!.data) }}</pre>
      } @else {
        <app-empty-state message="No output yet." />
      }
    </app-card>
  `,
})
export class PlaygroundComponent {
  private readonly api = inject(ApiService);

  protected chatMessage = '';
  protected toolMessage = 'What time is it in Warsaw?';
  protected structuredPrompt = 'A short note about hackathons';
  protected schemaText = EXAMPLE_SCHEMA;
  protected readonly schemaError = signal<string | null>(null);

  protected readonly chat = createResource<ChatResponse>();
  protected readonly tool = createResource<ToolChatResult>();
  protected readonly structured = createResource<StructuredResponse>();

  protected sendChat(): void {
    this.chat.load(this.api.chat({ message: this.chatMessage }));
  }

  protected sendToolChat(): void {
    this.tool.load(this.api.toolChat({ message: this.toolMessage }));
  }

  protected sendStructured(): void {
    try {
      const schema = JSON.parse(this.schemaText) as Record<string, unknown>;
      this.schemaError.set(null);
      this.structured.load(this.api.structured({ prompt: this.structuredPrompt, schema }));
    } catch {
      this.schemaError.set('Schema is not valid JSON');
    }
  }

  protected pretty(value: unknown): string {
    return JSON.stringify(value, null, 2);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, delay } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChatRequest,
  ChatResponse,
  Health,
  StructuredRequest,
  StructuredResponse,
  ToolChatRequest,
  ToolChatResult,
  ToolDefinition,
} from './models';

/**
 * Single place that talks to the Spring backend. With `environment.useMocks` every call is served from
 * `public/mock/<path-with-dashes>.json` (e.g. /llm/chat -> mock/llm-chat.json), so the UI can be built
 * without a running backend.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  health(): Observable<Health> {
    return this.get<Health>('/health');
  }

  tools(): Observable<ToolDefinition[]> {
    return this.get<ToolDefinition[]>('/llm/tools');
  }

  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.post<ChatResponse>('/llm/chat', request);
  }

  structured(request: StructuredRequest): Observable<StructuredResponse> {
    return this.post<StructuredResponse>('/llm/structured', request);
  }

  toolChat(request: ToolChatRequest): Observable<ToolChatResult> {
    return this.post<ToolChatResult>('/llm/tool-chat', request);
  }

  private get<T>(path: string): Observable<T> {
    if (environment.useMocks) {
      return this.mock<T>(path);
    }
    return this.http.get<T>(`${environment.apiUrl}${path}`);
  }

  private post<T>(path: string, body: unknown): Observable<T> {
    if (environment.useMocks) {
      return this.mock<T>(path);
    }
    return this.http.post<T>(`${environment.apiUrl}${path}`, body);
  }

  private mock<T>(path: string): Observable<T> {
    const file = path.replace(/^\//, '').replace(/\//g, '-');
    return this.http.get<T>(`mock/${file}.json`).pipe(delay(300));
  }
}

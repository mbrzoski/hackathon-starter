import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, delay } from 'rxjs';
import { environment } from '../../environments/environment';
import { Health } from './models';
import { FamilyDashboard, ListenState, SeniorState, Settings } from './view-models';

/**
 * Single place that talks to the Spring backend. With `environment.useMocks` every call is served from
 * `public/mock/<path-with-dashes>.json` (e.g. /family/dashboard -> mock/family-dashboard.json), so the UI can be built
 * without a running backend.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  health(): Observable<Health> {
    return this.get<Health>('/health');
  }

  familyDashboard(): Observable<FamilyDashboard> {
    return this.get<FamilyDashboard>('/family/dashboard');
  }

  settings(): Observable<Settings> {
    return this.get<Settings>('/settings');
  }

  seniorState(): Observable<SeniorState> {
    return this.get<SeniorState>('/senior/state');
  }

  listenState(): Observable<ListenState> {
    return this.get<ListenState>('/listen/state');
  }

  private get<T>(path: string): Observable<T> {
    if (environment.useMocks) {
      return this.mock<T>(path);
    }
    return this.http.get<T>(`${environment.apiUrl}${path}`);
  }

  private mock<T>(path: string): Observable<T> {
    const file = path.replace(/^\//, '').replace(/\//g, '-');
    return this.http.get<T>(`mock/${file}.json`).pipe(delay(300));
  }
}

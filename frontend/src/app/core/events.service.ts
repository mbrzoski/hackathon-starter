import { DestroyRef, Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { StatusService } from '../api/api/status.service';
import {
  Alert,
  AlertCreatedEvent,
  AlertDecisionEvent,
  CallEndedEvent,
  CallStartedEvent,
  Decision,
  EventEnvelope,
  Mode,
  PhoneCall,
  PhoneCallEvent,
  RiskUpdate,
  RiskUpdateEvent,
  SystemStatus,
  SystemStatusComponentEnum,
  SystemStatusStateEnum,
  SystemStatusEvent,
  TranscriptSegment,
  TranscriptSegmentEvent,
} from '../api/model/models';
import { createEventValidator } from './event-validator';

export type EventsRole = 'senior' | 'family' | 'audit';
export type ConnectionState = 'connecting' | 'open' | 'closed';

/** UI view of the current call, built from call.started / call.ended. */
export interface ActiveCall {
  callId: string;
  startedAt: string;
  endedAt: string | null;
  hadAlert: boolean | null;
  /** The backend restarted during the call and no longer knows it: nothing about it will arrive any more. */
  interrupted?: boolean;
}

/** Creates the socket; replaced by a fake in tests. */
export const WEB_SOCKET_FACTORY = new InjectionToken<(url: string) => WebSocket>('WEB_SOCKET_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new WebSocket(url),
});

/** Start time of the running backend (GET /api/status); a new value means it restarted. Replaced in tests. */
export const BACKEND_STARTED_AT = new InjectionToken<() => Promise<string>>('BACKEND_STARTED_AT', {
  providedIn: 'root',
  factory: () => {
    const status = inject(StatusService);
    return async () => (await firstValueFrom(status.getStatus())).startedAt;
  },
});

const ROUTE_ROLES: Record<string, EventsRole> = {
  senior: 'senior',
  listen: 'senior',
  family: 'family',
  setup: 'family',
  audit: 'audit',
};

const INITIAL_BACKOFF_MS = 1000;
const MAX_BACKOFF_MS = 10_000;

/**
 * The only client of /ws/events. Every message is validated against EventEnvelope from the contract (FE-13)
 * and folded into signals. The role follows the current route; reconnects with backoff up to 10 s.
 */
@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly createSocket = inject(WEB_SOCKET_FACTORY);
  private readonly backendStartedAt = inject(BACKEND_STARTED_AT);
  private readonly router = inject(Router);

  private readonly _connection = signal<ConnectionState>('closed');
  private readonly _mode = signal<Mode | null>(null);
  private readonly _systemStatus = signal<Partial<Record<SystemStatusComponentEnum, SystemStatus>>>({});
  private readonly _activeCall = signal<ActiveCall | null>(null);
  private readonly _segments = signal<TranscriptSegment[]>([]);
  private readonly _risk = signal<RiskUpdate | null>(null);
  private readonly _alerts = signal<Alert[]>([]);
  private readonly _decisions = signal<Decision[]>([]);
  private readonly _phoneCall = signal<PhoneCall | null>(null);
  /** When the simulated phone call was switched on (the `at` of its event). */
  private readonly _phoneCallAt = signal<string | null>(null);

  readonly connection = this._connection.asReadonly();
  readonly mode = this._mode.asReadonly();
  readonly systemStatus = this._systemStatus.asReadonly();
  readonly activeCall = this._activeCall.asReadonly();
  readonly segments = this._segments.asReadonly();
  readonly risk = this._risk.asReadonly();
  readonly alerts = this._alerts.asReadonly();
  readonly decisions = this._decisions.asReadonly();
  /** The simulated incoming phone call (demo) while it is on, else null. */
  readonly phoneCall = this._phoneCall.asReadonly();
  /**
   * The listening device is streaming right now: the phone call is on and the backend reported the audio path ok
   * after the call began (an older "ok" is left over from an earlier session). A lost, paused or silent stream is
   * not "recording" (OBS-01).
   */
  readonly recording = computed(() => {
    const call = this._phoneCall();
    const since = this._phoneCallAt();
    const audio = this._systemStatus().audio;
    return (
      !!call &&
      !!since &&
      audio?.state === SystemStatusStateEnum.ok &&
      Date.parse(audio.at) >= Date.parse(since)
    );
  });
  readonly online = computed(() => this._connection() === 'open');

  private readonly validator = createEventValidator();
  private socket: WebSocket | null = null;
  private role: EventsRole | null = null;
  private backoffMs = INITIAL_BACKOFF_MS;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  /** startedAt of the backend this client last talked to. */
  private knownBackendStart: string | null = null;

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        const role = roleForUrl(e.urlAfterRedirects);
        if (role) {
          this.connect(role);
        }
      });
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  /** Connects as the given role; a no-op if already connected (or connecting) with it. */
  connect(role: EventsRole): void {
    if (this.role === role && this.socket) {
      return;
    }
    this.disconnect();
    this.role = role;
    this.backoffMs = INITIAL_BACKOFF_MS;
    this.open();
  }

  disconnect(): void {
    this.role = null;
    this.clearReconnect();
    const socket = this.socket;
    this.socket = null;
    socket?.close();
    this._connection.set('closed');
  }

  private open(): void {
    const role = this.role;
    if (!role) {
      return;
    }
    this._connection.set('connecting');
    // The snapshot after connecting brings it back if it is still on; one switched off meanwhile must not linger.
    this._phoneCall.set(null);
    const socket = this.createSocket(eventsUrl(role));
    this.socket = socket;
    socket.onopen = () => {
      this.backoffMs = INITIAL_BACKOFF_MS;
      this._connection.set('open');
      void this.checkBackendRestart(this._activeCall()?.callId ?? null);
    };
    socket.onmessage = (msg: MessageEvent) => this.receive(msg.data);
    socket.onclose = () => {
      if (this.socket !== socket) {
        return;
      }
      this.socket = null;
      this._connection.set('closed');
      this.scheduleReconnect();
    };
  }

  /**
   * The backend keeps a call only in memory. After a restart it does not know the call that was going on, so no
   * call.ended will ever come for it: mark it interrupted so screens stop showing it as ongoing (UC-01 test, bug 1b).
   */
  private async checkBackendRestart(callIdAtOpen: string | null): Promise<void> {
    let startedAt: string;
    try {
      startedAt = await this.backendStartedAt();
    } catch {
      return; // Unknown: keep the state. The socket will tell if the backend is gone.
    }
    const restarted = this.knownBackendStart !== null && this.knownBackendStart !== startedAt;
    this.knownBackendStart = startedAt;
    if (!restarted || callIdAtOpen === null) {
      return;
    }
    this._activeCall.update((call) =>
      call && call.callId === callIdAtOpen && !call.endedAt
        ? { ...call, endedAt: new Date().toISOString(), interrupted: true }
        : call,
    );
  }

  private scheduleReconnect(): void {
    this.clearReconnect();
    const delay = this.backoffMs;
    this.backoffMs = Math.min(this.backoffMs * 2, MAX_BACKOFF_MS);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.open();
    }, delay);
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private receive(raw: unknown): void {
    let data: unknown;
    try {
      data = JSON.parse(String(raw));
    } catch {
      // Not the message itself: it may hold a transcript (FF-09).
      console.warn('Rejected /ws/events message that is not JSON');
      return;
    }
    if (this.validator(data)) {
      this.apply(data);
    }
  }

  private apply(event: EventEnvelope): void {
    this._mode.set(event.mode);
    // The generated union is not discriminated (`type: any`), so narrow on the validated type string.
    switch (event.type as string) {
      case 'call.started': {
        const { payload } = event as CallStartedEvent;
        // The reconnect snapshot repeats call.started for the ongoing call: keep its state (FF-02).
        if (this._activeCall()?.callId === payload.callId) {
          break;
        }
        this._activeCall.set({ callId: payload.callId, startedAt: event.at, endedAt: null, hadAlert: null });
        this._segments.set([]);
        this._risk.set(null);
        this._alerts.set([]);
        this._decisions.set([]);
        break;
      }
      case 'call.ended': {
        const { payload } = event as CallEndedEvent;
        this._activeCall.update((call) =>
          call && call.callId === payload.callId ? { ...call, endedAt: event.at, hadAlert: payload.hadAlert } : call,
        );
        break;
      }
      case 'transcript.segment': {
        const segment = (event as TranscriptSegmentEvent).payload;
        // Interim results are replaced by later versions of the same segment.
        this._segments.update((list) => {
          const i = list.findIndex((s) => s.segId === segment.segId);
          return i < 0 ? [...list, segment] : list.map((s, j) => (j === i ? segment : s));
        });
        break;
      }
      case 'risk.update':
        this._risk.set((event as RiskUpdateEvent).payload);
        break;
      case 'alert.created': {
        const alert = (event as AlertCreatedEvent).payload;
        this._alerts.update((list) => [...list.filter((a) => a.alertId !== alert.alertId), alert]);
        break;
      }
      case 'phone.call': {
        const call = (event as PhoneCallEvent).payload;
        this._phoneCall.set(call.active ? call : null);
        this._phoneCallAt.set(call.active ? event.at : null);
        break;
      }
      case 'alert.decision':
        this._decisions.update((list) => [...list, (event as AlertDecisionEvent).payload]);
        break;
      case 'system.status': {
        const status = (event as SystemStatusEvent).payload;
        this._systemStatus.update((all) => ({ ...all, [status.component]: status }));
        break;
      }
    }
  }
}

export function roleForUrl(url: string): EventsRole | null {
  const first = url.split(/[/?#]/).find((part) => part.length > 0) ?? '';
  return ROUTE_ROLES[first] ?? null;
}

/** Same origin as the page (WEB-02); wss: when the page is served over https: (WEB-03). */
export function eventsUrl(role: EventsRole, loc: Pick<Location, 'protocol' | 'host'> = location): string {
  const scheme = loc.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${scheme}//${loc.host}/ws/events?role=${role}`;
}

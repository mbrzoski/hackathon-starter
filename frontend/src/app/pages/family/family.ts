import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DataService } from '../../api/api/data.service';
import { DemoService } from '../../api/api/demo.service';
import { EventsService } from '../../core/events.service';
import { SettingsStore } from '../../core/settings.store';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { problemDetailText } from '../../shared/problem-detail';
import { AlertCard } from './alert-card';
import { FamilyFeed } from './family-feed';
import { SystemStatusBar } from '../../shared/system-status-bar';

/** Family panel: mobile first from 360 px (WEB-12). Alerts newest first; levels and texts come from the backend. */
@Component({
  selector: 'app-family',
  imports: [AlertCard, Icon, ModeBadge, RouterLink, SystemStatusBar],
  templateUrl: './family.html',
  styleUrl: './family.scss',
})
export class Family {
  protected readonly feed = inject(FamilyFeed);
  private readonly events = inject(EventsService);
  private readonly demo = inject(DemoService);
  private readonly data = inject(DataService);
  /** The result of the last "Usuń dane tego połączenia"; never silent. */
  protected readonly deleteMessage = signal<{ ok: boolean; text: string } | null>(null);

  protected readonly simulating = signal(false);
  protected readonly simulateError = signal<string | null>(null);
  protected readonly phoneCall = this.events.phoneCall;

  private readonly settings = inject(SettingsStore);
  /** Both consents missing in the saved settings: listening will be refused (AUD-07). */
  protected readonly consentMissing = computed(() => this.settings.consented() === false);

  constructor() {
    this.settings.load();
  }

  /**
   * "Zakończ połączenie" on an alert of the ongoing call: ends the simulated phone call, so /listen stops listening
   * and the call ends everywhere; a scripted demo call is stopped too. Nothing is dialled or cut (FE-09).
   */
  protected endCall(): void {
    this.simulateError.set(null);
    this.demo.setPhoneCall({ active: false }).subscribe({
      error: (err: unknown) => this.simulateError.set(`Nie udało się zakończyć połączenia. ${problemDetailText(err)}`),
    });
    this.demo.stopReplay().subscribe({ error: () => undefined });
  }

  /** Demo: switches the simulated incoming call on or off. It shows on /senior and makes /listen listen. */
  protected toggleCall(): void {
    this.simulating.set(true);
    this.simulateError.set(null);
    this.demo.setPhoneCall({ active: !this.phoneCall() }).subscribe({
      next: () => this.simulating.set(false),
      error: (err: unknown) => {
        this.simulating.set(false);
        this.simulateError.set(`Nie udało się zmienić symulacji połączenia. ${problemDetailText(err)}`);
      },
    });
  }

  /** Erases everything stored about one call (DELETE /api/calls/{callId}); its cards leave the panel. */
  protected deleteCall(callId: string): void {
    this.deleteMessage.set(null);
    this.data.deleteCallData(callId).subscribe({
      next: () => {
        this.feed.removeCall(callId);
        this.deleteMessage.set({ ok: true, text: 'Usunięto dane połączenia.' });
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 404) {
          // Nothing stored on the server (already erased or never kept): drop the cards here too.
          this.feed.removeCall(callId);
          this.deleteMessage.set({ ok: true, text: 'Usunięto dane połączenia.' });
          return;
        }
        this.deleteMessage.set({ ok: false, text: `Nie udało się usunąć danych połączenia. ${problemDetailText(err)}` });
      },
    });
  }

  protected isLive(callId: string): boolean {
    const call = this.events.activeCall();
    return call?.callId === callId && call.endedAt === null;
  }

  /** The live transcript belongs only to the ongoing call's alerts. */
  protected segmentsFor(callId: string) {
    return this.events.activeCall()?.callId === callId ? this.events.segments() : [];
  }
}

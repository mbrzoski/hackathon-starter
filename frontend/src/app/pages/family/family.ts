import { Component, computed, effect, inject, signal } from '@angular/core';
import { DemoService } from '../../api/api/demo.service';
import { EventsService } from '../../core/events.service';
import { SettingsStore, FAMILY_PHONE_PATTERN } from '../../core/settings.store';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { problemDetailText } from '../../shared/problem-detail';
import { AlertCard } from './alert-card';
import { FamilyFeed } from './family-feed';
import { SystemStatusBar } from '../../shared/system-status-bar';

/** Family panel: mobile first from 360 px (WEB-12). Alerts newest first; levels and texts come from the backend. */
@Component({
  selector: 'app-family',
  imports: [AlertCard, Icon, ModeBadge, SystemStatusBar],
  templateUrl: './family.html',
  styleUrl: './family.scss',
})
export class Family {
  protected readonly feed = inject(FamilyFeed);
  private readonly events = inject(EventsService);
  private readonly demo = inject(DemoService);

  protected readonly simulating = signal(false);
  protected readonly simulateError = signal<string | null>(null);
  protected readonly phoneCall = this.events.phoneCall;

  private readonly settings = inject(SettingsStore);
  /** The number being typed; starts as the saved one. */
  protected readonly phoneDraft = signal('');
  private draftTouched = false;
  protected readonly phoneValid = computed(() => FAMILY_PHONE_PATTERN.test(this.phoneDraft().trim()));
  protected readonly configState = signal<'idle' | 'saving' | 'saved' | 'failed'>('idle');

  constructor() {
    this.settings.load();
    effect(() => {
      const saved = this.settings.familyPhone();
      if (saved !== null && !this.draftTouched) {
        this.phoneDraft.set(saved);
      }
    });
  }

  protected editPhone(value: string): void {
    this.draftTouched = true;
    this.phoneDraft.set(value);
    this.configState.set('idle');
  }

  /** Saves the number the senior's "Zadzwoń do bliskiej osoby" offers. Empty removes it. */
  protected saveConfig(): void {
    if (!this.phoneValid()) {
      return;
    }
    this.configState.set('saving');
    this.settings.save(this.phoneDraft()).subscribe({
      next: (config) => {
        this.phoneDraft.set(config.familyPhone);
        this.configState.set('saved');
      },
      error: () => this.configState.set('failed'),
    });
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

  protected isLive(callId: string): boolean {
    const call = this.events.activeCall();
    return call?.callId === callId && call.endedAt === null;
  }

  /** The live transcript belongs only to the ongoing call's alerts. */
  protected segmentsFor(callId: string) {
    return this.events.activeCall()?.callId === callId ? this.events.segments() : [];
  }
}

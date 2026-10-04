import { Injectable, inject, signal } from '@angular/core';
import { SettingsService } from '../api/api/settings.service';
import { Settings } from '../api/model/models';

export interface TrustedContact {
  name: string;
  phone: string;
}

/**
 * Household settings the screens need, from GET /api/settings (consents, trusted contacts, the senior's name). The
 * only source of numbers to dial (FE-10): the first trusted contact of the setup wizard.
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly settingsApi = inject(SettingsService);

  readonly firstContact = signal<TrustedContact | null>(null);
  /** The senior as the family calls them ("Mama") and their number, for the family panel's call link. */
  readonly senior = signal<TrustedContact | null>(null);
  /** Both consents given in the setup wizard (AUD-07). Null until loaded. */
  readonly consented = signal<boolean | null>(null);

  /** Reads the settings. A failure leaves what is known (the screens never block on it). */
  load(): void {
    this.settingsApi.getSettings().subscribe({
      next: (settings) => this.applySettings(settings),
      error: () => undefined,
    });
  }

  /** New settings from the backend (loaded, or just saved by the setup wizard). */
  applySettings(settings: Settings): void {
    this.consented.set(settings.seniorConsent && settings.familyConsent);
    if (settings.seniorName) {
      this.senior.set({ name: settings.seniorName, phone: this.senior()?.phone ?? '' });
    }
    const first = settings.contacts[0];
    this.firstContact.set(first ? { name: first.name, phone: first.phone } : null);
  }
}

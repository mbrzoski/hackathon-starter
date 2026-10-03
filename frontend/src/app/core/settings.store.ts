import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { SeniorConfigService } from '../api/api/senior-config.service';
import { SettingsService } from '../api/api/settings.service';
import { SeniorConfig, Settings } from '../api/model/models';

export interface TrustedContact {
  /** Empty when only the number is known (the family's number from the senior's configuration). */
  name: string;
  phone: string;
}

/** "a, b" or one per line -> trimmed words, no empties, no duplicates (the backend cleans the same way). */
export function parseKeywords(text: string): string[] {
  const seen = new Map<string, string>();
  for (const part of text.split(/[\n,;]+/)) {
    const word = part.trim().replace(/\s+/g, ' ');
    if (word.length >= 2) seen.set(word.toLowerCase(), seen.get(word.toLowerCase()) ?? word);
  }
  return [...seen.values()];
}

export const MAX_KEYWORDS = 30;
export const MAX_KEYWORD_LENGTH = 60;

/** What a family number may look like; the same rule as the contract (`SeniorConfig.familyPhone`). */
export const FAMILY_PHONE_PATTERN = /^(\+?[0-9 ]{9,15})?$/;

/**
 * Household settings the screens need, from GET /api/senior-config (family number, the family's words) and
 * GET /api/settings (consents, trusted contacts, the senior's name). The only source of numbers to dial (FE-10): the
 * family number when it is set, otherwise the first trusted contact of the setup wizard.
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly api = inject(SeniorConfigService);
  private readonly settingsApi = inject(SettingsService);
  private readonly settings = signal<Settings | null>(null);

  readonly firstContact = signal<TrustedContact | null>(null);
  /** The number saved in the senior's configuration, "" when none. Null until it has been loaded. */
  readonly familyPhone = signal<string | null>(null);
  /** The words the family wants the profile to be sensitive to. Null until loaded. */
  readonly keywords = signal<string[] | null>(null);
  /** The senior as the family calls them ("Mama") and their number, for the family panel's call link. */
  readonly senior = signal<TrustedContact | null>(null);
  /** Both consents given in the setup wizard (AUD-07). Null until loaded. */
  readonly consented = signal<boolean | null>(null);

  /** Reads the configuration. A failure leaves what is known (the screens never block on it). */
  load(): void {
    this.api.getSeniorConfig().subscribe({
      next: (config) => this.apply(config),
      error: () => undefined,
    });
    this.settingsApi.getSettings().subscribe({
      next: (settings) => this.applySettings(settings),
      error: () => undefined,
    });
  }

  /** New settings from the backend (loaded, or just saved by the setup wizard). */
  applySettings(settings: Settings): void {
    this.settings.set(settings);
    this.consented.set(settings.seniorConsent && settings.familyConsent);
    if (settings.seniorName) {
      this.senior.set({ name: settings.seniorName, phone: this.senior()?.phone ?? '' });
    }
    this.updateContact();
  }

  /** Saves the configuration; the observable fails when the backend refuses it. */
  save(phone: string, keywords: readonly string[]): Observable<SeniorConfig> {
    return this.api
      .setSeniorConfig({ familyPhone: phone.trim(), keywords: [...keywords] })
      .pipe(tap((config) => this.apply(config)));
  }

  private apply(config: SeniorConfig): void {
    this.familyPhone.set(config.familyPhone);
    this.keywords.set(config.keywords);
    this.updateContact();
  }

  private updateContact(): void {
    const phone = this.familyPhone();
    const first = this.settings()?.contacts[0];
    this.firstContact.set(phone ? { name: '', phone } : first ? { name: first.name, phone: first.phone } : null);
  }
}

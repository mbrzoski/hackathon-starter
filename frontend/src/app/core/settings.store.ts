import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { SeniorConfigService } from '../api/api/senior-config.service';
import { SeniorConfig } from '../api/model/models';

export interface TrustedContact {
  /** Empty when only the number is known (the family's number from the senior's configuration). */
  name: string;
  phone: string;
}

/** What a family number may look like; the same rule as the contract (`SeniorConfig.familyPhone`). */
export const FAMILY_PHONE_PATTERN = /^(\+?[0-9 ]{9,15})?$/;

/**
 * Household settings the screens need. The only source of numbers to dial (FE-10): the family number of the
 * senior's configuration (GET/PUT /api/senior-config), set on the family panel.
 * TODO: the rest (consents, contacts, sensitivity) comes with GET /api/settings, which is not in the contract yet.
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly api = inject(SeniorConfigService);

  readonly firstContact = signal<TrustedContact | null>(null);
  /** The number saved in the senior's configuration, "" when none. Null until it has been loaded. */
  readonly familyPhone = signal<string | null>(null);
  /** The senior as the family calls them ("Mama") and their number, for the family panel's call link. */
  readonly senior = signal<TrustedContact | null>(null);

  /** Reads the configuration. A failure leaves what is known (the screens never block on it). */
  load(): void {
    this.api.getSeniorConfig().subscribe({
      next: (config) => this.apply(config),
      error: () => undefined,
    });
  }

  /** Saves the family number; the observable fails when the backend refuses it. */
  save(phone: string): Observable<SeniorConfig> {
    return this.api.setSeniorConfig({ familyPhone: phone.trim() }).pipe(tap((config) => this.apply(config)));
  }

  private apply(config: SeniorConfig): void {
    this.familyPhone.set(config.familyPhone);
    this.firstContact.set(config.familyPhone ? { name: '', phone: config.familyPhone } : null);
  }
}

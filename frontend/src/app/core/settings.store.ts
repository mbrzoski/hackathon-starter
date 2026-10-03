import { Injectable, signal } from '@angular/core';

export interface TrustedContact {
  name: string;
  phone: string;
}

/**
 * Household settings the screens need. The only source of numbers to dial (FE-10).
 * TODO: load through the generated service once GET /api/settings is in contracts/openapi.yaml (it is not yet).
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  readonly firstContact = signal<TrustedContact | null>(null);
  /** The senior as the family calls them ("Mama") and their number, for the family panel's call link. */
  readonly senior = signal<TrustedContact | null>(null);
}

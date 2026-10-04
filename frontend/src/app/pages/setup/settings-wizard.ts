import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SettingsService } from '../../api/api/settings.service';
import { Sensitivity, Settings } from '../../api/model/models';
import { SettingsStore } from '../../core/settings.store';
import { Icon } from '../../shared/icon';
import { problemDetailText } from '../../shared/problem-detail';

type Step = 'consent' | 'contacts' | 'sensitivity' | 'retention' | 'summary';

interface ContactDraft {
  name: string;
  phone: string;
}

const STEPS: readonly { id: Step; title: string }[] = [
  { id: 'consent', title: 'Zgody' },
  { id: 'contacts', title: 'Zaufane kontakty' },
  { id: 'sensitivity', title: 'Czułość' },
  { id: 'retention', title: 'Przechowywanie' },
  { id: 'summary', title: 'Podsumowanie' },
];

/** Same rule as the contract (`Settings.contacts[].phone`). */
const PHONE = /^\+?[0-9 ]{9,15}$/;
export const MAX_CONTACTS = 5;

export const SENSITIVITY_TEXT: Record<Sensitivity, { label: string; text: string }> = {
  calm: {
    label: 'Spokojna',
    text: 'Mniej ostrzeżeń. Ostrzeżenie pojawia się dopiero przy dwóch oznakach oszustwa, a wysokie przy trzech.',
  },
  standard: {
    label: 'Standardowa',
    text: 'Zalecana. Dwie różne oznaki oszustwa dają ostrzeżenie, a prośba o pieniądze pod presją daje wysokie.',
  },
  sensitive: {
    label: 'Czuła',
    text: 'Więcej ostrzeżeń, także fałszywych. Już jedna oznaka oszustwa daje ostrzeżenie.',
  },
};

/**
 * Setup wizard (FE-06, architecture 6.4): consents in plain language, trusted contacts, sensitivity, retention.
 * Every field is required: both consents, the senior's name, at least one trusted contact, sensitivity and retention.
 * Saved with PUT /api/settings, then back to the family panel. Without both consents the listening device does not
 * start (AUD-07). The settings never go to the AI (rule 5): the screen says so.
 */
@Component({
  selector: 'app-settings-wizard',
  imports: [Icon],
  template: `
    <section class="wizard" aria-labelledby="wizard-title">
      <h2 id="wizard-title">Konfiguracja</h2>
      @if (loadError(); as error) {
        <p class="problem" role="alert"><app-icon name="warning" [size]="24" /> Nie udało się wczytać ustawień: {{ error }}</p>
        <button type="button" class="secondary" (click)="load()">Spróbuj ponownie</button>
      } @else if (!loaded()) {
        <p role="status">Wczytuję ustawienia…</p>
      } @else {
        <ol class="steps">
          @for (s of steps; track s.id; let i = $index) {
            <li [class.current]="s.id === step()" [attr.aria-current]="s.id === step() ? 'step' : null">
              <button type="button" class="step-link" (click)="go(s.id)">{{ i + 1 }}. {{ s.title }}</button>
            </li>
          }
        </ol>

        @switch (step()) {
          @case ('consent') {
            <div class="panel">
              <h3>Na co się zgadzacie</h3>
              <ul class="facts">
                <li>Urządzenie przy telefonie słucha rozmów, żeby rozpoznać typowe etapy oszustwa.</li>
                <li>Dźwięk jest rozpoznawany na miejscu i nigdy nie jest zapisywany.</li>
                <li>Tekst rozmowy trafia do AI (Anthropic), żeby ocenić, czy to oszustwo. Imiona i numery z ustawień nie trafiają do AI.</li>
                <li>Fragmenty rozmów, które wywołały ostrzeżenie, są przechowywane przez wybrany czas i można je usunąć.</li>
                <li>Przy ostrzeżeniu informowana jest rodzina. System nigdy sam nie rozłącza, nie dzwoni i nie blokuje numerów.</li>
              </ul>
              <label class="check">
                <input type="checkbox" required [checked]="seniorConsent()" (change)="seniorConsent.set($any($event.target).checked)" />
                Senior zgadza się na nasłuch rozmów. <span class="req">(wymagane)</span>
              </label>
              <label class="check">
                <input type="checkbox" required [checked]="familyConsent()" (change)="familyConsent.set($any($event.target).checked)" />
                Rodzina zgadza się na otrzymywanie ostrzeżeń. <span class="req">(wymagane)</span>
              </label>
              @if (!seniorConsent() || !familyConsent()) {
                <p class="note warn" role="status">Bez obu zgód Nasłuch nie włączy mikrofonu.</p>
              }
              <label for="senior-name">Jak nazywacie seniora, np. „Mama” <span class="req">(wymagane)</span></label>
              <input id="senior-name" required maxlength="60" [value]="seniorName()" [attr.aria-invalid]="!nameValid()"
                     (input)="seniorName.set($any($event.target).value)" />
            </div>
          }
          @case ('contacts') {
            <div class="panel">
              <h3>Zaufane kontakty</h3>
              <p class="note">Do tych osób senior może zadzwonić po ostrzeżeniu. Co najmniej jedna, najwyżej {{ maxContacts }}. <span class="req">(wymagane)</span></p>
              @if (!contacts().length) {
                <p class="problem" role="alert">Dodaj co najmniej jeden zaufany kontakt.</p>
              }
              @for (c of contacts(); track $index; let i = $index) {
                <div class="contact">
                  <label [for]="'contact-name-' + i">Imię</label>
                  <input [id]="'contact-name-' + i" maxlength="100" [value]="c.name" (input)="editContact(i, 'name', $any($event.target).value)" />
                  <label [for]="'contact-phone-' + i">Numer</label>
                  <input [id]="'contact-phone-' + i" type="tel" inputmode="tel" [value]="c.phone"
                         [attr.aria-invalid]="!contactValid(c)" (input)="editContact(i, 'phone', $any($event.target).value)" />
                  <button type="button" class="secondary" (click)="removeContact(i)">Usuń</button>
                </div>
              }
              @if (!contactsValid()) {
                <p class="problem" role="alert">Każdy kontakt potrzebuje imienia i numeru w formacie +48 601 234 567.</p>
              }
              @if (contacts().length < maxContacts) {
                <button type="button" class="secondary" (click)="addContact()">Dodaj kontakt</button>
              }
            </div>
          }
          @case ('sensitivity') {
            <div class="panel" role="radiogroup" aria-labelledby="sens-title">
              <h3 id="sens-title">Czułość ostrzeżeń <span class="req">(wymagane)</span></h3>
              @for (s of sensitivities; track s) {
                <label class="radio">
                  <input type="radio" name="sensitivity" [value]="s" [checked]="sensitivity() === s" (change)="sensitivity.set(s)" />
                  <span><strong>{{ sensitivityText[s].label }}</strong> {{ sensitivityText[s].text }}</span>
                </label>
              }
            </div>
          }
          @case ('retention') {
            <div class="panel">
              <h3>Jak długo przechowywać ostrzeżenia</h3>
              <label for="retention">Liczba dni (1 do 90) <span class="req">(wymagane)</span></label>
              <input id="retention" type="number" min="1" max="90" [value]="retentionDays()"
                     [attr.aria-invalid]="!retentionValid()" (input)="retentionDays.set(+$any($event.target).value)" />
              <p class="note">Starsze ostrzeżenia, fragmenty rozmów, decyzje i audyt są usuwane automatycznie.</p>
              @if (!retentionValid()) {
                <p class="problem" role="alert">Podaj liczbę dni od 1 do 90.</p>
              }
            </div>
          }
          @case ('summary') {
            <div class="panel">
              <h3>Podsumowanie</h3>
              <ul class="facts">
                <li>Zgoda seniora: <strong>{{ seniorConsent() ? 'tak' : 'nie' }}</strong>, zgoda rodziny: <strong>{{ familyConsent() ? 'tak' : 'nie' }}</strong></li>
                <li>Kontakty: <strong>{{ contacts().length }}</strong></li>
                <li>Czułość: <strong>{{ sensitivityText[sensitivity()].label }}</strong></li>
                <li>Przechowywanie: <strong>{{ retentionDays() }} dni</strong></li>
              </ul>
            </div>
          }
        }

        <div class="actions">
          @if (index() > 0) {
            <button type="button" class="secondary" (click)="back()">Wstecz</button>
          }
          @if (index() < steps.length - 1) {
            <button type="button" class="secondary" (click)="next()">Dalej</button>
          }
          <button type="button" class="primary" [disabled]="!valid() || saving()" (click)="save()">Zapisz ustawienia</button>
        </div>
        @if (missing().length) {
          <div class="missing" role="status">
            <p>Wszystkie pola są wymagane. Uzupełnij:</p>
            <ul>
              @for (m of missing(); track m.text) {
                <li><button type="button" class="step-link" (click)="go(m.step)">{{ m.text }}</button></li>
              }
            </ul>
          </div>
        }
        @if (saved()) {
          <p class="ok" role="status"><app-icon name="check" [size]="24" /> Zapisano ustawienia.</p>
        }
        @if (saveError(); as error) {
          <p class="problem" role="alert"><app-icon name="warning" [size]="24" /> Nie udało się zapisać: {{ error }}</p>
        }
      }
    </section>
  `,
  styles: `
    .wizard { display: grid; gap: 12px; }
    h2 { font-size: 18px; margin: 16px 0 0; }
    h3 { font-size: 17px; margin: 0 0 8px; }
    .steps { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 6px; }
    .step-link { font: inherit; font-size: 14px; min-height: 36px; padding: 0 12px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--surface); color: var(--text); cursor: pointer; }
    .current .step-link { background: var(--primary); color: #fff; border-color: var(--primary); font-weight: 700; }
    .panel { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); }
    .facts { margin: 0; padding-left: 20px; display: grid; gap: 6px; }
    .check, .radio { display: flex; gap: 10px; align-items: flex-start; font-weight: 600; }
    .check input, .radio input { width: 22px; height: 22px; margin-top: 2px; flex: none; }
    input:not([type='checkbox']):not([type='radio']) { font: inherit; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; min-height: 44px; }
    input[aria-invalid='true'] { border-color: var(--alarm); }
    .contact { display: grid; grid-template-columns: auto 1fr; gap: 6px 10px; align-items: center; padding: 10px; border-radius: 8px; background: var(--bg); }
    .contact button { grid-column: 2; justify-self: start; }
    .note { margin: 0; color: var(--muted); font-size: 14px; }
    .note.warn { color: var(--warn-deep); font-weight: 700; }
    .req { font-weight: 600; color: var(--muted); font-size: 14px; }
    .missing { padding: 10px 14px; border-radius: 8px; background: var(--bg); }
    .missing p { margin: 0 0 6px; font-weight: 700; }
    .missing ul { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: 6px; }
    .problem { display: flex; align-items: center; gap: 8px; margin: 0; padding: 8px 12px; border-radius: 8px; background: var(--warn-bg); color: var(--warn-deep); font-weight: 700; }
    .ok { display: flex; align-items: center; gap: 8px; margin: 0; font-weight: 700; }
    .actions { display: flex; flex-wrap: wrap; gap: 12px; }
    button { font: inherit; font-weight: 700; min-height: 48px; padding: 0 22px; border-radius: 10px; border: 2px solid var(--primary); cursor: pointer; }
    .primary { background: var(--primary); color: #fff; }
    .secondary { background: var(--surface); color: var(--primary); }
    button:disabled { opacity: 0.6; cursor: default; }
    button:focus-visible { outline: 3px solid var(--highlight); outline-offset: 2px; }
  `,
})
export class SettingsWizard {
  private readonly api = inject(SettingsService);
  private readonly store = inject(SettingsStore);
  private readonly router = inject(Router);

  protected readonly steps = STEPS;
  protected readonly sensitivities: Sensitivity[] = [Sensitivity.calm, Sensitivity.standard, Sensitivity.sensitive];
  protected readonly sensitivityText = SENSITIVITY_TEXT;
  protected readonly maxContacts = MAX_CONTACTS;

  protected readonly step = signal<Step>('consent');
  protected readonly index = computed(() => STEPS.findIndex((s) => s.id === this.step()));

  protected readonly loaded = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly seniorConsent = signal(false);
  protected readonly familyConsent = signal(false);
  protected readonly seniorName = signal('');
  protected readonly contacts = signal<ContactDraft[]>([]);
  protected readonly sensitivity = signal<Sensitivity>(Sensitivity.standard);
  protected readonly retentionDays = signal(30);

  protected readonly contactValid = (c: ContactDraft) => c.name.trim().length > 0 && PHONE.test(c.phone.trim());
  protected readonly contactsValid = computed(() => this.contacts().every(this.contactValid));
  protected readonly retentionValid = computed(() => {
    const days = this.retentionDays();
    return Number.isInteger(days) && days >= 1 && days <= 90;
  });
  protected readonly nameValid = computed(() => this.seniorName().trim().length > 0);
  /** What is still missing, with the step where it is filled in. Every field is required. */
  protected readonly missing = computed(() => {
    const list: { text: string; step: Step }[] = [];
    if (!this.seniorConsent()) list.push({ text: 'zgoda seniora', step: 'consent' });
    if (!this.familyConsent()) list.push({ text: 'zgoda rodziny', step: 'consent' });
    if (!this.nameValid()) list.push({ text: 'jak nazywacie seniora', step: 'consent' });
    if (!this.contacts().length) list.push({ text: 'co najmniej jeden zaufany kontakt', step: 'contacts' });
    else if (!this.contactsValid()) list.push({ text: 'poprawne imię i numer każdego kontaktu', step: 'contacts' });
    if (!this.retentionValid()) list.push({ text: 'czas przechowywania (1 do 90 dni)', step: 'retention' });
    return list;
  });
  protected readonly valid = computed(() => this.missing().length === 0);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loadError.set(null);
    try {
      this.apply(await firstValueFrom(this.api.getSettings()));
      this.loaded.set(true);
    } catch (err) {
      this.loadError.set(problemDetailText(err));
    }
  }

  protected go(step: Step): void {
    this.step.set(step);
  }

  protected next(): void {
    this.step.set(STEPS[Math.min(this.index() + 1, STEPS.length - 1)].id);
  }

  protected back(): void {
    this.step.set(STEPS[Math.max(this.index() - 1, 0)].id);
  }

  protected addContact(): void {
    this.contacts.update((list) => (list.length < MAX_CONTACTS ? [...list, { name: '', phone: '' }] : list));
  }

  protected removeContact(i: number): void {
    this.contacts.update((list) => list.filter((_, j) => j !== i));
  }

  protected editContact(i: number, field: keyof ContactDraft, value: string): void {
    this.saved.set(false);
    this.contacts.update((list) => list.map((c, j) => (j === i ? { ...c, [field]: value } : c)));
  }

  protected async save(): Promise<void> {
    if (!this.valid()) {
      return;
    }
    this.saving.set(true);
    this.saved.set(false);
    this.saveError.set(null);
    const body: Settings = {
      seniorConsent: this.seniorConsent(),
      familyConsent: this.familyConsent(),
      seniorName: this.seniorName().trim(),
      contacts: this.contacts().map((c) => ({ name: c.name.trim(), phone: c.phone.trim() })),
      sensitivity: this.sensitivity(),
      retentionDays: this.retentionDays(),
    };
    try {
      const stored = await firstValueFrom(this.api.setSettings(body));
      this.apply(stored);
      this.store.applySettings(stored);
      this.saved.set(true);
      // Saved: back to the main view of the family panel.
      await this.router.navigateByUrl('/family');
    } catch (err) {
      this.saveError.set(problemDetailText(err));
    } finally {
      this.saving.set(false);
    }
  }

  private apply(s: Settings): void {
    this.seniorConsent.set(s.seniorConsent);
    this.familyConsent.set(s.familyConsent);
    this.seniorName.set(s.seniorName);
    this.contacts.set(s.contacts.map((c) => ({ name: c.name, phone: c.phone })));
    this.sensitivity.set(s.sensitivity);
    this.retentionDays.set(s.retentionDays);
  }
}

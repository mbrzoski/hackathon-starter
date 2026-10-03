import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { SeniorConfigService } from '../../api/api/senior-config.service';
import { SettingsService } from '../../api/api/settings.service';
import { Settings } from '../../api/model/models';
import { SettingsStore } from '../../core/settings.store';
import { SettingsWizard } from './settings-wizard';

const SAVED: Settings = {
  seniorConsent: false,
  familyConsent: false,
  contacts: [{ name: 'Marek', phone: '+48 601 234 567' }],
  sensitivity: 'standard' as Settings['sensitivity'],
  retentionDays: 30,
  seniorName: '',
};

describe('SettingsWizard (FE-06)', () => {
  let api: { getSettings: ReturnType<typeof vi.fn>; setSettings: ReturnType<typeof vi.fn> };

  const el = (f: ComponentFixture<SettingsWizard>) => f.nativeElement as HTMLElement;
  const text = (f: ComponentFixture<SettingsWizard>) => el(f).textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (f: ComponentFixture<SettingsWizard>, label: string) =>
    [...el(f).querySelectorAll('button')].find((b) => b.textContent?.trim() === label) as HTMLButtonElement;
  const type = async (f: ComponentFixture<SettingsWizard>, selector: string, value: string) => {
    const input = el(f).querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await f.whenStable();
  };
  const tick = async (f: ComponentFixture<SettingsWizard>, selector: string) => {
    el(f).querySelector<HTMLInputElement>(selector)!.click();
    await f.whenStable();
  };

  async function open() {
    TestBed.configureTestingModule({
      imports: [SettingsWizard],
      providers: [
        { provide: SettingsService, useValue: api },
        { provide: SeniorConfigService, useValue: { getSeniorConfig: () => of({ familyPhone: '', keywords: [] }) } },
      ],
    });
    const fixture = TestBed.createComponent(SettingsWizard);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    api = { getSettings: vi.fn(() => of(SAVED)), setSettings: vi.fn((s: Settings) => of(s)) };
  });

  it('starts with the consents in plain language and warns that nothing listens without them', async () => {
    const fixture = await open();
    expect(text(fixture)).toContain('Dźwięk jest rozpoznawany na miejscu i nigdy nie jest zapisywany.');
    expect(text(fixture)).toContain('Imiona i numery z ustawień nie trafiają do AI.');
    expect(text(fixture)).toContain('Bez obu zgód Nasłuch nie włączy mikrofonu.');
  });

  it('saves the consents, the contacts, the sensitivity and the retention', async () => {
    const fixture = await open();
    const checks = el(fixture).querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    checks[0].click();
    checks[1].click();
    await type(fixture, '#senior-name', ' Mama ');
    await fixture.whenStable();
    expect(text(fixture)).not.toContain('Bez obu zgód');

    button(fixture, 'Dalej').click();
    await fixture.whenStable();
    button(fixture, 'Dodaj kontakt').click();
    await fixture.whenStable();
    await type(fixture, '#contact-name-1', 'Ela');
    await type(fixture, '#contact-phone-1', '500 600 700');

    button(fixture, 'Dalej').click();
    await fixture.whenStable();
    await tick(fixture, 'input[value="sensitive"]');
    expect(text(fixture)).toContain('Już jedna oznaka oszustwa daje ostrzeżenie.');

    button(fixture, 'Dalej').click();
    await fixture.whenStable();
    await type(fixture, '#retention', '14');

    button(fixture, 'Zapisz ustawienia').click();
    await fixture.whenStable();

    expect(api.setSettings).toHaveBeenCalledWith({
      seniorConsent: true,
      familyConsent: true,
      seniorName: 'Mama',
      contacts: [
        { name: 'Marek', phone: '+48 601 234 567' },
        { name: 'Ela', phone: '500 600 700' },
      ],
      sensitivity: 'sensitive',
      retentionDays: 14,
    });
    expect(text(fixture)).toContain('Zapisano ustawienia.');
    expect(TestBed.inject(SettingsStore).consented()).toBe(true);
  });

  it('does not save a contact without a valid number, nor a retention outside 1..90', async () => {
    const fixture = await open();
    button(fixture, '2. Zaufane kontakty').click();
    await fixture.whenStable();
    await type(fixture, '#contact-phone-0', 'abc');
    expect(text(fixture)).toContain('Każdy kontakt potrzebuje imienia i numeru');
    expect(button(fixture, 'Zapisz ustawienia').disabled).toBe(true);

    await type(fixture, '#contact-phone-0', '+48 601 234 567');
    button(fixture, '4. Przechowywanie').click();
    await fixture.whenStable();
    await type(fixture, '#retention', '120');
    expect(text(fixture)).toContain('Podaj liczbę dni od 1 do 90.');
    expect(button(fixture, 'Zapisz ustawienia').disabled).toBe(true);
  });

  it('says so when saving failed (never silent)', async () => {
    api.setSettings.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = await open();
    button(fixture, 'Zapisz ustawienia').click();
    await fixture.whenStable();
    expect(el(fixture).querySelector('[role="alert"]')?.textContent).toContain('Nie udało się zapisać');
  });
});

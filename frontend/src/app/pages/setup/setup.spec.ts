import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ProtectionService } from '../../api/api/protection.service';
import { SettingsService } from '../../api/api/settings.service';
import { provideRouter } from '@angular/router';
import { Alert, Decision, Mode, Protection, SystemStatus, TranscriptSegment } from '../../api/model/models';
import { ActiveCall, ConnectionState, EventsService } from '../../core/events.service';
import { Setup } from './setup';

const events = {
  connection: signal<ConnectionState>('open'),
  mode: signal<Mode | null>(Mode.SCRIPTED),
  systemStatus: signal<Partial<Record<string, SystemStatus>>>({}),
  activeCall: signal<ActiveCall | null>(null),
  segments: signal<TranscriptSegment[]>([]),
  alerts: signal<Alert[]>([]),
  decisions: signal<Decision[]>([]),
};

describe('Setup: protection switch', () => {
  let api: {
    getProtection: ReturnType<typeof vi.fn<() => Observable<Protection>>>;
    setProtection: ReturnType<typeof vi.fn<(p: Protection) => Observable<Protection>>>;
  };

  const text = (f: ComponentFixture<Setup>) => (f.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (f: ComponentFixture<Setup>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent?.trim() === label);

  const extraProviders = () => [
    {
      provide: SettingsService,
      useValue: {
        getSettings: () =>
          of({ seniorConsent: false, familyConsent: false, contacts: [], sensitivity: 'standard', retentionDays: 30, seniorName: '' }),
        setSettings: vi.fn(),
      },
    },
    provideRouter([]),
  ];

  async function open(enabled = true) {
    api.getProtection.mockReturnValue(of({ enabled }));
    TestBed.configureTestingModule({
      imports: [Setup],
      providers: [
        { provide: ProtectionService, useValue: api },
        { provide: EventsService, useValue: events },
        {
          provide: SettingsService,
          useValue: {
            getSettings: () =>
              of({ seniorConsent: false, familyConsent: false, contacts: [], sensitivity: 'standard', retentionDays: 30, seniorName: '' }),
            setSettings: vi.fn(),
          },
        },
        provideRouter([]),
      ],
    });
    const fixture = TestBed.createComponent(Setup);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    api = { getProtection: vi.fn(), setProtection: vi.fn((p: Protection) => of(p)) };
  });

  it('says in words and with an icon that protection is on', async () => {
    const fixture = await open(true);
    expect(text(fixture)).toContain('Ochrona jest włączona');
    expect(button(fixture, 'Wyłącz ochronę')).toBeTruthy();
  });

  it('asks once more before switching off, and "Anuluj" changes nothing', async () => {
    const fixture = await open(true);
    button(fixture, 'Wyłącz ochronę')!.click();
    await fixture.whenStable();
    expect(text(fixture)).toContain('Na pewno wyłączyć ochronę?');
    expect(api.setProtection).not.toHaveBeenCalled();

    button(fixture, 'Anuluj')!.click();
    await fixture.whenStable();
    expect(api.setProtection).not.toHaveBeenCalled();
    expect(text(fixture)).toContain('Ochrona jest włączona');
  });

  it('switches off after the confirmation and says what that means', async () => {
    const fixture = await open(true);
    button(fixture, 'Wyłącz ochronę')!.click();
    await fixture.whenStable();
    button(fixture, 'Tak, wyłącz ochronę')!.click();
    await fixture.whenStable();

    expect(api.setProtection).toHaveBeenCalledWith({ enabled: false });
    expect(text(fixture)).toContain('Ochrona jest wyłączona');
    expect(text(fixture)).toContain('nic nie ostrzeże seniora');
  });

  it('switches back on with one tap', async () => {
    const fixture = await open(false);
    button(fixture, 'Włącz ochronę')!.click();
    await fixture.whenStable();
    expect(api.setProtection).toHaveBeenCalledWith({ enabled: true });
    expect(text(fixture)).toContain('Ochrona jest włączona');
  });

  it('never claims a change that did not happen', async () => {
    const fixture = await open(false);
    api.setProtection.mockReturnValue(throwError(() => new Error('500')));
    button(fixture, 'Włącz ochronę')!.click();
    await fixture.whenStable();

    expect(text(fixture)).toContain('Nie udało się zmienić ustawienia');
    expect(text(fixture)).toContain('Ochrona jest wyłączona');
  });

  it('says so when the setting cannot be read, and offers a retry', async () => {
    api.getProtection.mockReturnValue(throwError(() => new Error('down')));
    TestBed.configureTestingModule({
      imports: [Setup],
      providers: [
        { provide: ProtectionService, useValue: api },
        { provide: EventsService, useValue: events },
        ...extraProviders(),
      ],
    });
    const fixture = TestBed.createComponent(Setup);
    await fixture.whenStable();
    expect(text(fixture)).toContain('Nie udało się sprawdzić ustawienia ochrony.');

    api.getProtection.mockReturnValue(of({ enabled: true }));
    button(fixture, 'Spróbuj ponownie')!.click();
    await fixture.whenStable();
    expect(text(fixture)).toContain('Ochrona jest włączona');
  });
});

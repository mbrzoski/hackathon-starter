import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuditService } from '../../api/api/audit.service';
import { CallsService } from '../../api/api/calls.service';
import { DemoService } from '../../api/api/demo.service';
import { Alert, AuditRecord, Decision, Mode, SystemStatus, TranscriptSegment } from '../../api/model/models';
import { ActiveCall, ConnectionState, EventsService } from '../../core/events.service';
import { Audit, aiOnlyStages } from './audit';

const AT = '2026-10-04T10:00:00Z';

const RECORD = {
  id: 1, callId: 'c1', mode: 'SCRIPTED', recordedAt: AT, segmentRange: 's1-s4', model: 'claude-sonnet-5-5', effort: 'low',
  usage: { inputTokens: 1200, cacheReadInputTokens: 3000, outputTokens: 80, cacheCreationInputTokens: 0 },
  latencyMs: 2800, stopReason: 'end_turn', textCleared: false, late: false, levelBefore: 'low', levelAfter: 'high',
  hits: [
    { stage: 'SECRECY_DEMAND', segId: 's3', quote: 'to musi zostać między nami', speakerRole: 'caller', source: 'llm', validated: true },
    { stage: 'AUTHORITY_CLAIM', segId: 's1', quote: 'komisarz', speakerRole: 'caller', source: 'llm', validated: true },
    { stage: 'MONEY_REQUEST', segId: 's4', quote: 'wymyślony cytat', speakerRole: 'caller', source: 'llm', validated: false },
  ],
  keywordHits: [{ stage: 'AUTHORITY_CLAIM', segId: 's1', quote: 'Tu komisarz.', speakerRole: 'unclear', source: 'keywords', validated: true }],
} as unknown as AuditRecord;

describe('Audit (FE-07)', () => {
  const events = {
    connection: signal<ConnectionState>('open'),
    mode: signal<Mode | null>(Mode.SCRIPTED),
    systemStatus: signal<Partial<Record<string, SystemStatus>>>({}),
    activeCall: signal<ActiveCall | null>(null),
    segments: signal<TranscriptSegment[]>([]),
    alerts: signal<Alert[]>([]),
    decisions: signal<Decision[]>([]),
    phoneCall: signal(null),
  };
  let demo: { listScenarios: ReturnType<typeof vi.fn>; startReplay: ReturnType<typeof vi.fn>; stopReplay: ReturnType<typeof vi.fn> };
  let calls: { listCalls: ReturnType<typeof vi.fn>; getCallAudit: ReturnType<typeof vi.fn> };

  const el = (f: ComponentFixture<Audit>) => f.nativeElement as HTMLElement;
  const text = (f: ComponentFixture<Audit>) => el(f).textContent?.replace(/\s+/g, ' ') ?? '';
  const button = (f: ComponentFixture<Audit>, label: string) =>
    [...el(f).querySelectorAll('button')].find((b) => b.textContent?.includes(label)) as HTMLButtonElement;

  async function open() {
    TestBed.configureTestingModule({
      imports: [Audit],
      providers: [
        { provide: EventsService, useValue: events },
        { provide: DemoService, useValue: demo },
        { provide: CallsService, useValue: calls },
        {
          provide: AuditService,
          useValue: {
            getAuditSummary: () =>
              of({
                aiCalls: 10, conversations: 2, mockCallsExcluded: 3, latencyP50Ms: 2800, latencyP95Ms: 3900,
                avgCostPerCallUsd: 0.0031, avgCostPerConversationUsd: 0.0155, rejectedQuotes: 1, lateResults: 0,
                errorsByCause: { TIMEOUT: 1 }, pricing: {}, costNote: 'Koszt z cennika.',
              }),
          },
        },
        provideRouter([]),
      ],
    });
    const fixture = TestBed.createComponent(Audit);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    events.activeCall.set(null);
    demo = {
      listScenarios: vi.fn(() => of([{ scenarioId: '01-fake-police-classic', title: 'Klasyczny fałszywy policjant', description: 'Opis' }])),
      startReplay: vi.fn(() => of({})),
      stopReplay: vi.fn(() => of({})),
    };
    calls = {
      listCalls: vi.fn(() => of([{ callId: 'c1', mode: 'SCRIPTED', startedAt: AT, endedAt: AT, maxLevel: 'high', aiCalls: 1 }])),
      getCallAudit: vi.fn(() => of([RECORD])),
    };
  });

  it('plays the chosen scenario in SCRIPTED mode at the chosen speed, and stops it', async () => {
    const fixture = await open();
    button(fixture, 'Odtwórz (SCRIPTED)').click();
    await fixture.whenStable();
    expect(demo.startReplay).toHaveBeenCalledWith({ scenarioId: '01-fake-police-classic', mode: 'SCRIPTED', speed: 1 });

    button(fixture, 'Zatrzymaj').click();
    await fixture.whenStable();
    expect(demo.stopReplay).toHaveBeenCalled();
  });

  it('does not start a scenario while a call is going on, and says why a start failed', async () => {
    demo.startReplay.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const fixture = await open();
    button(fixture, 'Odtwórz (SCRIPTED)').click();
    await fixture.whenStable();
    expect(el(fixture).querySelector('[role="alert"]')?.textContent).toContain('Nie udało się odtworzyć scenariusza');

    events.activeCall.set({ callId: 'c2', startedAt: AT, endedAt: null, hadAlert: null });
    await fixture.whenStable();
    expect(button(fixture, 'Odtwórz (SCRIPTED)').disabled).toBe(true);
  });

  it('shows the measured numbers of real AI calls', async () => {
    const fixture = await open();
    expect(text(fixture)).toContain('2.8 s');
    expect(text(fixture)).toContain('3.9 s');
    expect(text(fixture)).toContain('Pominięte wywołania MOCK3');
    expect(text(fixture)).toContain('TIMEOUT: 1');
  });

  it('shows every AI call of a call: tokens, latency, checked and rejected quotes, and what only the AI found', async () => {
    const fixture = await open();
    button(fixture, 'Szczegóły').click();
    await fixture.whenStable();

    expect(calls.getCallAudit).toHaveBeenCalledWith('c1');
    expect(text(fixture)).toContain('s1-s4');
    expect(text(fixture)).toContain('1200 we');
    expect(el(fixture).querySelector('li.rejected')?.textContent).toContain('wymyślony cytat');
    expect(el(fixture).querySelector('.ai-only')?.textContent).toContain('Prośba o tajemnicę');
    expect(el(fixture).querySelector('.ai-only')?.textContent).not.toContain('Podanie się za funkcjonariusza');
  });

  it('aiOnlyStages ignores rejected quotes and stages the keywords found too', () => {
    expect(aiOnlyStages(RECORD)).toEqual(['SECRECY_DEMAND']);
  });
});

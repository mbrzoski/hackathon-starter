import { TestBed } from '@angular/core/testing';
import { Alert, TranscriptSegment } from '../api/model/models';
import { TranscriptDebugPanel, highlightParts, keywordHighlights } from './transcript-debug-panel';

const seg = (segId: string, text: string, isFinal: boolean, tStartMs = 65_000) =>
  ({ callId: 'c1', segId, tStartMs, tEndMs: tStartMs + 2000, text, isFinal, speaker: 'A' }) as unknown as TranscriptSegment;

describe('highlightParts', () => {
  it('underlines the quote where it was said, ignoring case and diacritics', () => {
    expect(highlightParts('Proszę nikomu nie mówić, dobrze?', ['prosze nikomu nie mowic'])).toEqual([
      { text: 'Proszę nikomu nie mówić', hit: true },
      { text: ', dobrze?', hit: false },
    ]);
  });

  it('returns the whole text when there is no hit', () => {
    expect(highlightParts('dzień dobry', ['kod blik'])).toEqual([{ text: 'dzień dobry', hit: false }]);
  });
});

describe('keywordHighlights', () => {
  it('takes only keyword hits of the alerts', () => {
    const alert = {
      stages: [
        { segId: 's1', quote: 'kod blik', source: 'keywords' },
        { segId: 's2', quote: 'przelew', source: 'llm' },
      ],
    } as unknown as Alert;
    expect(keywordHighlights([alert])).toEqual([{ segId: 's1', quote: 'kod blik' }]);
  });
});

describe('TranscriptDebugPanel', () => {
  async function render(segments: TranscriptSegment[], highlights: { segId: string; quote: string }[] = []) {
    const fixture = TestBed.createComponent(TranscriptDebugPanel);
    fixture.componentRef.setInput('segments', segments);
    fixture.componentRef.setInput('highlights', highlights);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows segId and time, interim marked and final plain', async () => {
    const el = await render([seg('s1', 'Dzień dobry', true), seg('s2', 'tu poli', false, 70_000)]);
    const rows = el.querySelectorAll('.row');
    expect(rows.length).toBe(2);
    expect(rows[0].classList.contains('interim')).toBe(false);
    expect(rows[0].textContent).toContain('s1 · 01:05');
    expect(rows[1].classList.contains('interim')).toBe(true);
    expect(rows[1].textContent).toContain('wstępny');
  });

  it('underlines keyword hits in the right segment only', async () => {
    const el = await render([seg('s1', 'Podaj kod BLIK', true), seg('s2', 'Podaj kod BLIK', true)], [
      { segId: 's2', quote: 'kod blik' },
    ]);
    const rows = el.querySelectorAll('.row');
    expect(rows[0].querySelector('u')).toBeNull();
    expect(rows[1].querySelector('u')?.textContent).toBe('kod BLIK');
  });

  it('says so when there are no segments', async () => {
    expect((await render([])).textContent).toContain('Brak segmentów');
  });
});

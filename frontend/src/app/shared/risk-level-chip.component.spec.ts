import { TestBed } from '@angular/core/testing';
import { RISK_LEVEL_WORDS, RiskLevelChipComponent } from './risk-level-chip.component';

describe('RiskLevelChipComponent', () => {
  it('maps levels to Polish words', () => {
    expect(RISK_LEVEL_WORDS).toEqual({ none: 'Brak', low: 'Niskie', medium: 'Średnie', high: 'Wysokie' });
  });

  it('renders the word and never a number or percent', () => {
    const fixture = TestBed.createComponent(RiskLevelChipComponent);
    fixture.componentRef.setInput('level', 'high');
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text.trim()).toBe('Wysokie');
    expect(text).not.toMatch(/\d|%/);
  });
});

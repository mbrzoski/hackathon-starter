import { RiskLevel } from '../api/model/models';
import { RISK_LEVEL_WORDS } from './risk-level-chip';

describe('RISK_LEVEL_WORDS', () => {
  it('uses words, never numbers or percentages (FE-07)', () => {
    for (const level of Object.values(RiskLevel)) {
      expect(RISK_LEVEL_WORDS[level]).toMatch(/^\p{L}+$/u);
    }
  });
});

import { formatDuration } from './time';

describe('formatDuration', () => {
  it('formats seconds as mm:ss', () => {
    expect(formatDuration(0)).toBe('00:00');
    expect(formatDuration(65)).toBe('01:05');
    expect(formatDuration(222.9)).toBe('03:42');
  });
});

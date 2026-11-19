import { describe, expect, it } from 'vitest';
import { formatNumber, formatTime } from './format';

describe('formatTime', () => {
  it('returns placeholder for empty values', () => {
    expect(formatTime(null)).toBe('-');
    expect(formatTime(undefined)).toBe('-');
    expect(formatTime(0)).toBe('-');
    expect(formatTime(-1)).toBe('-');
  });

  it('formats millisecond timestamps', () => {
    expect(formatTime(new Date(2026, 0, 2, 3, 4, 5).getTime())).toBe('2026-01-02 03:04:05');
  });
});

describe('formatNumber', () => {
  it('returns placeholder for null', () => {
    expect(formatNumber(null)).toBe('-');
  });

  it('formats numbers with grouping', () => {
    expect(formatNumber(12345)).toBe((12345).toLocaleString());
  });
});

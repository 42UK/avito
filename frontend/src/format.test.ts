import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatPercent, humanize } from './format';

describe('Russian UI formatting', () => {
  it('formats currency and percentages without inventing missing values', () => {
    expect(formatMoney(0, 'RUB')).toContain('0');
    expect(formatPercent(19.5)).toBe('19,5%');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('handles invalid dates and known API enums', () => {
    expect(formatDate('not-a-date')).toBe('—');
    expect(humanize('CURRENT_MATCHED_LISTINGS')).toBe('Текущие объявления с совпавшим товаром');
  });
});

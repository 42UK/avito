export function formatMoney(value: number | null | undefined, currency = 'RUB'): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  try {
    return new Intl.NumberFormat('ru-RU', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(value)} ${currency}`;
  }
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return 'Ещё не запускался';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function formatPercent(value: number | null | undefined, fractionDigits = 1): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  return `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: fractionDigits }).format(value)}%`;
}

export function formatScore(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
}

export function humanize(value: string | null | undefined): string {
  if (!value) return 'Не указано';
  const labels: Record<string, string> = {
    ACTIVE: 'Активно',
    REMOVED: 'Снято',
    USED: 'Б/у',
    NEW: 'Новое',
    LIKE_NEW: 'Как новое',
    HIGH: 'Высокая',
    MEDIUM: 'Средняя',
    LOW: 'Низкая',
    CURRENT_MATCHED_LISTINGS: 'Текущие объявления с совпавшим товаром',
  };
  return labels[value] || value.replaceAll('_', ' ').toLocaleLowerCase('ru-RU');
}

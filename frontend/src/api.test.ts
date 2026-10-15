import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('REST API client', () => {
  it('requests the implemented paginated endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [], page: 2, size: 10, totalElements: 0, totalPages: 0,
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const result = await api.deals(2, 10);
    expect(fetchMock).toHaveBeenCalledWith('/api/deals?page=2&size=10', expect.any(Object));
    expect(result.page).toBe(2);
  });

  it('preserves API problem detail and reports unavailable API distinctly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 400, detail: 'Размер страницы должен быть положительным',
    }), { status: 400 })));
    await expect(api.stats()).rejects.toMatchObject({ message: 'Размер страницы должен быть положительным', status: 400 });

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(api.stats()).rejects.toThrow('Не удалось подключиться к API');
  });

  it('normalizes API not-found responses without converting them into empty results', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })));
    await expect(api.deal('missing')).rejects.toMatchObject({
      message: 'Запись не найдена или больше недоступна.',
      status: 404,
    });
  });

  it('sends user-provided costs to the BigDecimal profitability endpoint', async () => {
    const input = {
      purchasePrice: '1000',
      resalePrice: '1500',
      logisticsCost: '20',
      repairCost: '0',
      sellingFees: '50',
      otherCosts: '0',
      riskReserve: '30',
      minimumRequiredProfit: '100',
      pessimisticResalePrice: '1200',
      optimisticResalePrice: '1800',
      currency: 'RUB',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      currency: 'RUB',
      netProfit: 400,
      roiPercent: 38.1,
      scenarios: [],
    }), { status: 200 })));

    const result = await api.calculateProfitability(input);

    expect(fetch).toHaveBeenCalledWith('/api/profitability/calculate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }));
    expect(result.netProfit).toBe(400);
  });

  it('starts a saved search immediately through the run endpoint', async () => {
    const report = { processed: 3, duplicates: 1, created: 2, priceChanges: 0, removed: 0 };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(report), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.runSearch('search/1')).resolves.toEqual(report);

    expect(fetchMock).toHaveBeenCalledWith('/api/searches/search%2F1/run', expect.objectContaining({
      method: 'POST',
    }));
  });
});

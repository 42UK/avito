import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { api, ApiError } from '../api';
import { ProfitabilityCalculator } from './ProfitabilityCalculator';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('profitability calculator', () => {
  it('submits edited expenses and displays the backend calculation and scenarios', async () => {
    const calculate = vi.spyOn(api, 'calculateProfitability').mockResolvedValue({
      currency: 'RUB',
      purchasePrice: 1000,
      resalePrice: 1500,
      totalInvestment: 1050,
      netProfit: 400,
      roiPercent: 38.1,
      breakEvenPurchasePrice: 1400,
      maximumPurchasePrice: 1300,
      scenarios: [
        { name: 'PESSIMISTIC', resalePrice: 1200, netProfit: 100, roiPercent: 9.52 },
        { name: 'BASE', resalePrice: 1500, netProfit: 400, roiPercent: 38.1 },
        { name: 'OPTIMISTIC', resalePrice: 1800, netProfit: 700, roiPercent: 66.67 },
      ],
    });
    render(<ProfitabilityCalculator
      purchasePrice={1000}
      marketPrice={1500}
      p25={1200}
      p75={1800}
      currency="RUB"
    />);

    fireEvent.change(screen.getByLabelText('Логистика'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Пересчитать' }));

    await waitFor(() => expect(calculate).toHaveBeenCalledWith(expect.objectContaining({
      purchasePrice: '1000',
      resalePrice: '1500',
      logisticsCost: '25',
      pessimisticResalePrice: '1200',
      optimisticResalePrice: '1800',
      currency: 'RUB',
    })));
    expect(await screen.findByText('Чистая прибыль')).toBeInTheDocument();
    expect(screen.getByText('Чистая прибыль').parentElement).toHaveTextContent('400');
    expect(screen.getByText('Пессимистичный сценарий')).toBeInTheDocument();
    expect(screen.getByText('Максимальная цена покупки').parentElement).toHaveTextContent(/1\s*300/);
  });

  it('shows server validation failures instead of masking them', async () => {
    vi.spyOn(api, 'calculateProfitability').mockRejectedValue(
      new ApiError('Расходы должны быть неотрицательными', 400),
    );
    render(<ProfitabilityCalculator
      purchasePrice={1000}
      marketPrice={1500}
      p25={1200}
      p75={1800}
      currency="RUB"
    />);

    fireEvent.click(screen.getByRole('button', { name: 'Пересчитать' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Расходы должны быть неотрицательными');
  });
});

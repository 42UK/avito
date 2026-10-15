import { useState, type FormEvent } from 'react';
import { api, ApiError } from '../api';
import { formatMoney, formatPercent } from '../format';
import type { ProfitabilityInput, ProfitabilityResult } from '../types';

interface Props {
  purchasePrice: number;
  marketPrice: number;
  p25: number;
  p75: number;
  currency: string;
}

type CostField = 'logisticsCost' | 'repairCost' | 'sellingFees' | 'otherCosts' | 'riskReserve';

const costFields: { name: CostField; label: string }[] = [
  { name: 'logisticsCost', label: 'Логистика' },
  { name: 'repairCost', label: 'Ремонт' },
  { name: 'sellingFees', label: 'Комиссии продажи' },
  { name: 'otherCosts', label: 'Прочие расходы' },
  { name: 'riskReserve', label: 'Резерв на риск' },
];

export function ProfitabilityCalculator({ purchasePrice, marketPrice, p25, p75, currency }: Props) {
  const [costs, setCosts] = useState<Record<CostField, string>>({
    logisticsCost: '0',
    repairCost: '0',
    sellingFees: '0',
    otherCosts: '0',
    riskReserve: '0',
  });
  const [minimumRequiredProfit, setMinimumRequiredProfit] = useState('0');
  const [result, setResult] = useState<ProfitabilityResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [calculating, setCalculating] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setCalculating(true);
    const input: ProfitabilityInput = {
      purchasePrice: String(purchasePrice),
      resalePrice: String(marketPrice),
      ...costs,
      minimumRequiredProfit,
      pessimisticResalePrice: String(p25),
      optimisticResalePrice: String(p75),
      currency,
    };
    try {
      setResult(await api.calculateProfitability(input));
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Не удалось рассчитать прибыль.');
    } finally {
      setCalculating(false);
    }
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Расчёт прибыльности</h2>
          <p>Покупка — цена объявления; перепродажа — медиана рынка; сценарии — P25 / медиана / P75.</p>
        </div>
      </div>
      <p className="notice-inline">
        Расходы — ваши оценки, а не данные о фактических сделках. Значение 0 означает, что вы явно предполагаете отсутствие этой статьи.
      </p>
      <form className="profit-form" onSubmit={(event) => void submit(event)}>
        <div className="form-grid">
          {costFields.map(({ name, label }) => (
            <label key={name}>
              {label}
              <input
                type="number"
                min="0"
                step="0.01"
                required
                value={costs[name]}
                onChange={(event) => setCosts((previous) => ({ ...previous, [name]: event.target.value }))}
              />
            </label>
          ))}
          <label>
            Минимальная требуемая прибыль
            <input
              type="number"
              min="0"
              step="0.01"
              required
              value={minimumRequiredProfit}
              onChange={(event) => setMinimumRequiredProfit(event.target.value)}
            />
          </label>
        </div>
        {error && <div className="inline-error" role="alert">{error}</div>}
        <button className="button button-primary" disabled={calculating}>
          {calculating ? 'Рассчитываем…' : 'Пересчитать'}
        </button>
      </form>
      {result && (
        <div className="profit-results" aria-live="polite">
          <dl className="listing-facts">
            <Metric label="Чистая прибыль" value={formatMoney(result.netProfit, result.currency)} />
            <Metric label="ROI" value={formatPercent(result.roiPercent, 2)} />
            <Metric label="Общие вложения" value={formatMoney(result.totalInvestment, result.currency)} />
            <Metric label="Цена безубыточности" value={formatMoney(result.breakEvenPurchasePrice, result.currency)} />
            <Metric label="Максимальная цена покупки" value={formatMoney(result.maximumPurchasePrice, result.currency)} />
          </dl>
          <div className="profit-scenarios">
            {result.scenarios.map((scenario) => (
              <div key={scenario.name}>
                <span className="field-label">{scenarioLabel(scenario.name)}</span>
                <strong>{formatMoney(scenario.netProfit, result.currency)}</strong>
                <span className="muted">{formatPercent(scenario.roiPercent, 2)} ROI</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="metric"><dt>{label}</dt><dd>{value}</dd></div>;
}

function scenarioLabel(name: string): string {
  switch (name) {
    case 'PESSIMISTIC': return 'Пессимистичный сценарий';
    case 'BASE': return 'Базовый сценарий';
    case 'OPTIMISTIC': return 'Оптимистичный сценарий';
    default: return name;
  }
}

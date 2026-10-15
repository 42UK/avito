# Алгоритмы и QA

Принципы matching, расчёта рыночной цены и scoring описаны в
[`architecture/ARCHITECTURE.md`](../architecture/ARCHITECTURE.md) и контрактах
[`Product`](../contracts/product.md), [`MarketPrice`](../contracts/market-price.md),
[`DealScore`](../contracts/deal-score.md).

QA-артефакты MVP:

- [Стратегия тестирования matching/pricing/scoring](../qa/test-strategy.md)
- [Golden dataset (JSON Lines)](../qa/golden-dataset.jsonl)

Набор синтетический: он фиксирует ожидаемые продуктовые решения, а не реальные
объявления или окончательные параметры production-алгоритма.

## Текущий DealScoringService

Scoring получает candidate listing ID, сохранённый `marketPriceId` вместе с
`MarketPriceResult` и состоянием снимка, цену/валюту/состояние candidate,
`ProductMatchResult` и извлечённые risk flags. Pricing возвращает ID созданного
снимка через `PersistedMarketPrice`; scoring не читает репозитории pricing,
product или listing. В `DealScore.marketPriceId` сохраняется ID именно этого
снимка.

Скидка рассчитывается относительно `marketPrice`, никогда не относительно
минимума comparables:

```text
discountPercent = roundHalfUp((marketPrice - candidatePrice) / marketPrice * 100, 2)
dealScore       = clamp(roundHalfUp(max(discountPercent, 0) * 2, 2), 0, 100)
confidence      = roundHalfUp(marketConfidenceScore * productMatchConfidence, 4)
```

Если `marketPrice <= 0`, discount равен 0, добавляется
`MARKET_PRICE_UNAVAILABLE` и решение блокируется. `riskScore` — максимальная
тяжесть обнаруженных рисков: 100 для hard gate (дефект/запчасти, accessory,
несовпадение товара/валюты/состояния, LOW/нулевая confidence или недоступная
цена), 75 для скидки не ниже подозрительного порога, 60 для недостаточно
уверенного match и 35 для MEDIUM confidence; при отсутствии риска он равен 0.
Несколько причин не суммируются и не маскируют друг друга.

Только точный `MATCHED` с ненулевым Product ID того же товара, что в снимке,
match confidence не ниже порога, совпадающие currency и condition, отсутствие
defective/parts/accessory риска и `HIGH` confidence могут пройти deal gate.
MEDIUM всегда получает `REVIEW`; LOW и hard risks — `BLOCKED`. Скидка не ниже
подозрительного порога отправляется на ручную проверку. Состояние `UNKNOWN`
также требует проверки. Если все gates пройдены, `DEAL` требует одновременно
минимальную скидку и минимальный score; иначе результат `NO_DEAL`. Любое
отклонённое решение, кроме обычного `NO_DEAL`, ограничивает score настроенным
максимумом ниже deal-score threshold. Поэтому defect/parts и LOW/MEDIUM
confidence не могут выдать высокий score или разрешённую сделку.

Настройки находятся в `avito-deals.scoring` (значения по умолчанию):

| Ключ | Значение | Назначение |
|---|---:|---|
| `minimum-discount-percent` | 20 | Минимальная скидка для сделки |
| `minimum-deal-score` | 40 | Минимальный итоговый score; при формуле выше соответствует скидке 20% |
| `minimum-match-confidence` | 0.90 | Минимальная уверенность точного product match |
| `suspicious-discount-percent` | 60 | Скидка на этом уровне и выше требует REVIEW |
| `maximum-rejected-deal-score` | 25 | Верхняя граница score для BLOCKED/REVIEW, должна быть ниже минимального deal score |

`flags` содержат коды причин и один итоговый код `DEAL`, `NO_DEAL`, `BLOCKED`
или `REVIEW`; `DealScore.isDealAllowed()` истинно только для флага `DEAL`.
Каждый элемент `explanation` имеет непустые `code`, `parameters` и `text` и
покрывает market snapshot, скидку/недоступную цену, confidence, match, валюту,
состояние, обнаруженные риски и итоговое решение.

## Текущий расчёт MarketPrice

`MarketPriceService` принимает `ComparableListings` из публичного API pricing:
ID оцениваемого объявления, Product ID, точное состояние и валюту запроса,
плюс снимки объявлений с ID, Product ID, состоянием, валютой, суммой и признаком
активности. Сервис не обращается к репозиториям соседних модулей. Кандидат
исключается по ID повторно внутри расчёта; затем допускаются только активные
объявления того же Product, точного состояния и валюты.

Выбросы определяются fence-правилом IQR (1.5 IQR) и модифицированным MAD
(порог robust z-score 3.5); при нулевом MAD используется IQR, а при нулевых
MAD и IQR отличающиеся от медианы цены считаются выбросами. Для сохранения
устойчивого большинства результат фильтра ограничивается менее чем половиной
выборки. `sampleSize` — число оставшихся non-outlier объявлений; уверенность
HIGH даётся при размере не менее 5, иначе LOW. Числовой confidence score —
`min(sampleSize / 5, 1)`. Рыночная цена и медиана равны медиане оставшейся
выборки. p25/p75 рассчитываются linear interpolation на выборке после фильтра,
денежные значения округляются до 2 знаков HALF_UP. При пустой выборке цены и
confidence score равны нулю, confidence level — LOW; такой результат также
сохраняется как снапшот.

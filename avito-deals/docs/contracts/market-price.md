# Contract: MarketPrice
Status: Draft
`MarketPriceResult{productId, marketPrice, currency, median, p25, p75, sampleSize, confidenceScore 0..1, confidenceLevel LOW|MEDIUM|HIGH, outliers: UUID[], method, computedAt}`. `outliers` содержит ID comparable listings, исключённых робастным фильтром; длина списка равна `outliersCount`.

`confidenceScore` — числовая оценка уверенности, а `confidenceLevel` — её
категориальная интерпретация. Пороги `MEDIUM`/`HIGH` задаются конфигурацией
сервиса (`avito-deals.pricing.confidence.medium-min-sample-size` и
`avito-deals.pricing.confidence.high-min-sample-size`). По умолчанию выборка
менее 4 даёт `LOW`, ровно 4 — `MEDIUM`, от 5 — `HIGH`. Scoring не выдаёт
сделку при `confidenceLevel=LOW`.

Вход: `ComparableListings` без оцениваемого объявления. Минимальная цена не
используется как рыночная.

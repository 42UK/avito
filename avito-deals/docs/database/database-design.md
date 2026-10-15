# Database Design

PostgreSQL 16, миграции Flyway V1–V3. Цены и денежные значения используют
`numeric(14,2)` с отдельной валютой; `deal_score.discount_percent` хранит
`numeric(19,2)`. Время хранится как `timestamptz`.

| Таблица | Ключевые поля | Ограничения/индексы |
|---|---|---|
| seller | id, source, external_id, name | unique(source, external_id) |
| search | id, name, query, filters jsonb, min_deal_score, active, interval_minutes, last_run_at | `0 <= min_deal_score <= 100`; `interval_minutes > 0`; `last_run_at` добавлен V2 |
| listing | id, source, external_id, seller_id, title, description, price, currency, condition, location, url, status(ACTIVE/REMOVED), content_hash, first_seen_at, last_seen_at | unique(source, external_id); idx(status, last_seen_at); idx(price) |
| search_listing | search_id, listing_id, first_seen_at, last_seen_at | PK(search_id, listing_id); idx(listing_id) |
| listing_price_history | id, listing_id, price, currency, observed_at | append-only при изменении цены; idx(listing_id, observed_at) |
| product | id, canonical_key, brand, model, category | unique(canonical_key) |
| product_attribute | product_id, attribute_key, attribute_value | unique(product_id, attribute_key) |
| listing_product_match | listing_id, product_id, confidence, matcher_version, status | unique(listing_id); idx(product_id) |
| market_price | id, product_id, condition_group, median, p25, p75, market_price, currency, sample_size, confidence_score, confidence_level, outliers jsonb, outliers_count, method, computed_at | idx(product_id, condition_group, computed_at desc) |
| deal_score | id, listing_id, market_price_id, discount_percent numeric(19,2), deal_score, risk_score, confidence, flags jsonb, explanation jsonb, computed_at | idx(listing_id, computed_at desc); idx(deal_score desc) |
| notification | id, deal_score_id, channel, dedup_key, status, sent_at, attempt_count, last_attempt_at, error_code, error_message | unique(channel, dedup_key); `attempt_count >= 0`; delivery state расширен V3 |

Объявление дедуплицируется глобально по `unique(source, external_id)` и
сохраняется через `INSERT ... ON CONFLICT`. При каждом обнаружении отдельно
создаётся или обновляется связь в `search_listing`, поэтому одно объявление
может относиться к нескольким поискам без дублирования `listing`. Времена
обнаружения в этой таблице относятся к конкретному поиску; времена в `listing`
относятся к объявлению в целом.

`market_price.outliers` — JSON-массив UUID объявлений-выбросов, его размер
дублируется в `outliers_count` и проверяется ограничением БД.
`confidence_score` — числовая оценка `0..1`; `confidence_level` — категория
`LOW|MEDIUM|HIGH`. Пороги задаются настройками сервиса: по умолчанию менее
4 comparables дают `LOW`, ровно 4 — `MEDIUM`, от 5 — `HIGH`.

Retention: `listing_price_history` хранится. Очистка или агрегация `market_price`
в MVP не реализована: `deal_score` ссылается на снимок, а API выдаёт его
сохранённые значения и outlier IDs. Не удалять и не агрегировать эти строки без
отдельного правила retention, сохраняющего ссылки и семантику деталей сделки.
Остальные решения и границы производительности MVP — в
[performance audit](performance-audit.md).

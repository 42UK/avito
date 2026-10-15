# PERF-001 — аудит производительности запросов MVP

Аудит исходного кода и call paths выполнен 2026-10-08. Production DB,
нагрузочный профиль и целевой throughput не предоставлены; поэтому ниже —
статический анализ кода, а не измерение latency/throughput. Нагрузочные тесты,
production DB и новые инфраструктурные компоненты не использовались.

## Исправлено

- `DealQueryService.find` (`src/main/java/ru/avitodeals/scoring/application/DealQueryService.java`):
  прежде чем вернуть детальную сделку, сервис загружал всю историю `deal_score`
  через `latestScores()` и только затем выбирал последнюю оценку одного listing.
  Теперь он запрашивает только последнюю оценку этого listing через
  `DealScoreRepository.findFirstByListingIdOrderByComputedAtDescIdDesc`
  (`src/main/java/ru/avitodeals/scoring/infrastructure/persistence/DealScoreRepository.java`).
  Порядок `computed_at DESC, id DESC` сохранён, поэтому при одинаковом времени
  правило выбора latest осталось прежним. Условия запроса соответствуют левому
  префиксу существующего `idx_deal_score_listing_computed_at`; фактический
  выбор индекса планировщиком не измерялся. Новый индекс не добавлялся.
  Тесты проверяют тай-брейк по ID на H2 и что обработчик деталей не запускает
  глобальное чтение оценок.

## Подтверждённые ограничения и оставшиеся риски

- **Список сделок и `/stats` — неограниченная материализация истории оценок.**
  `DealQueryService.list` читает все `DealScore` через
  `findAllByOrderByComputedAtDescIdDesc`, в памяти оставляет latest per listing,
  фильтрует флаг `DEAL`, загружает listing IDs одним `findByIdIn` и лишь после
  этого применяет page/size. Поэтому API limit `1..100` ограничивает только
  размер ответа, не объём прочитанных оценок/обработанных listing IDs.
  `StatsController.stats` вызывает `DealQueryService.count`, который вызывает
  `list(0, 100)`: подсчёт eligible deals повторяет этот путь. Остальные четыре
  поля stats используют repository count-запросы. Это главный подтверждённый
  риск по мере роста таблицы оценок и listing. Переписывание отложено:
  запрос должен сохранять latest-score tie-break, отсеивать latest не-DEAL,
  исключать неактивные объявления и возвращать точный `totalElements`; безопасный
  SQL/projection path требует отдельного query-level integration test на
  PostgreSQL и плана выполнения на разрешённой непроизводственной базе.
  Код: `DealQueryService.java:50-66,109-117`,
  `DealScoreRepository.java:13`, `StatsController.java:24-28`,
  `ListingQueryService.java:46-50`.
- **Fanout планировщика.** `SearchScheduler.runDueSearches` каждую минуту
  материализует все активные Search (`findByActiveTrue`) и последовательно
  вызывает `claimIfDue` для каждой, включая ещё не наступившие по интервалу.
  Claim повторно проверяет due-время под row lock, что сохраняет корректность
  между экземплярами, но создаёт по одной проверке на каждую активную запись за
  тик и не имеет page/batch limit. Фильтр требует учитывать индивидуальный
  `interval_minutes`; лимитирование/параллелизм меняло бы задержку и порядок
  обработки, поэтому без SLA и реального количества поисков не менялись.
  Код: `SearchScheduler.java:37-59`, `SearchRepository.java:15-25`,
  `SearchRunClaimService.java:30-48`.
- **Ingestion — линейные запросы и память на размер snapshot.**
  `IngestionPersistence.persist` дедуплицирует полный `List<RawListing>` и
  последовательно вызывает `ListingCatalog.upsert` для каждой строки.
  `ListingCatalogService.upsert` делает поиск listing по source/external ID,
  а при seller ID — также поиск/сохранение seller; изменения цены сохраняются
  отдельной записью history. Это per-item persistence fanout, но не lazy-load
  N+1 в API view mapping; его нельзя безопасно свести к bulk write без
  сохранения seller resolution, цены/history, блокировок и событий на каждое
  объявление. `markMissing` также загружает все active listing данного source,
  но вызывается только для явно authoritative full-source snapshot; search
  snapshot не удаляет объявления.
  Код: `IngestionPersistence.java:43-85`,
  `ListingCatalogService.java:41-83,85-99`.
- **Детали сделки могут быть большими.** При `GET /deals/{id}` matched listing IDs
  и список listing загружаются пакетно, не отдельным запросом на comparable;
  затем API возвращает все текущие active comparables того же product/condition/
  currency и всю историю цен candidate. Ни у этих списков, ни у
  `priceHistory` нет лимита. Документированный basis
  `CURRENT_MATCHED_LISTINGS` означает полный текущий набор, а не историческую
  выборку расчёта; обрезка изменила бы API-семантику. Для ограничения потребуются
  отдельно согласованные pagination/retention контракты.
  Код: `DealQueryService.java:90-105`,
  `ProductQueryService.java:20-25`, `ListingQueryService.java:46-58`.
- **Pricing sample не ограничен.** `ComparableListings` копирует полученный
  список целиком, а calculator сортирует/обрабатывает все подходящие значения.
  Настроенные пороги confidence (по умолчанию 4/5) — пороги качества, не cap
  размера выборки. Cap мог бы изменить median/outliers, поэтому он не добавлялся.
  Код: `ComparableListings.java:9-25`, `MarketPriceCalculator.java:53-75`.
- **Прочие API-границы.** `/searches`, `/listings` и `/deals` валидируют page
  size в `1..100`; query paths для search/listing используют DB pagination.
  Listing view содержит scalar/UUID-поля без ленивых связанных коллекций, так
  что обычный `GET /listings` не создаёт entity N+1. Для deals пагинация
  применяется только после описанной полной материализации.
  Код: `SearchManagementService.java:24-29`,
  `ListingQueryService.java:30-36`, `ListingController.java:24-29`,
  `DealController.java:25-30`.
- **Retention.** История цены не очищается; migration не добавляет job очистки.
  Снапшоты market price также не удаляются: на них ссылаются DealScore, а детали
  выдают сохранённые значения/outlier IDs. Для обоих наборов данных рост пока
  не ограничен. Срок или агрегация не выбраны без политики хранения и оценки
  реального объёма; нельзя считать ранее упоминавшиеся 90 дней работающей
  очисткой.

## Индексы и запросы

V1 уже содержит `idx_deal_score_listing_computed_at`,
`idx_listing_product_match_product_id`,
`idx_listing_price_history_listing_observed_at`,
`idx_listing_status_last_seen_at` и индекс `search_listing(listing_id)`;
PK/unique покрывают также ключевые lookups. Индекс оценок по listing/time
совпадает с левым префиксом per-listing latest detail lookup; фактический выбор
индекса и стоимость ID tie-break сортировки не проверялись. `search` не имеет
отдельного индекса по `active`, а composite deal-score индекс не включает ID
tie-break. В аудите не найдено данных `EXPLAIN (ANALYZE, BUFFERS)` или замеров cardinality, поэтому
новые индексы не предложены: сначала нужны безопасные representative query
plans и объёмы данных. Индекс по одному boolean или speculative расширение
composite индекса без этих данных не обоснованы.
Определения индексов: `V1__initial_schema.sql:173-185`.

## Реальная граница MVP / проверка

`MockListingSource` содержит ровно две статические записи; тест проверяет её
результаты, но этот fixture не ограничивает интерфейс `ListingSource.fetch` и
не предсказывает объём production snapshot. Golden dataset предназначен для
точечных matching/pricing/scoring кейсов, не для throughput. Следовательно,
результаты тестов подтверждают семантику и текущий mock path, но не пропускную
способность, latency, безопасное число поисков, deals или comparables. Перед
оптимизацией оставшихся full/unbounded paths требуются целевые объёмы, допустимые
API contracts и representative non-production DB plans.
Код fixture: `MockListingSource.java:16-22`.

Проверки PERF-001: `./gradlew clean test build`, `git diff --check`.

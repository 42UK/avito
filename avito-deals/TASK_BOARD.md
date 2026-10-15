# TASK_BOARD

Статусы: TODO, IN_PROGRESS, BLOCKED, READY_FOR_REVIEW, CHANGES_REQUESTED, READY_FOR_TEST, PASSED, FAILED, DONE.

| ID | Title | Owner | Depends on | Status | Review |
|---|---|---|---|---|---|
| ARCH-001 | Architecture, ADR, DB design, module boundaries, contracts | architect | — | DONE | reviewer: APPROVED; зависимости, связь search-listing и confidence уточнены |
| DOCS-001 | Структура /docs, README | docs | — | DONE | review approved; docs index and database schema synchronized with Flyway V1–V3 |
| QA-001 | Тест-стратегия и golden dataset для matching/pricing/scoring | qa | — | DONE | тест-стратегия и 10 golden cases проверены; dataset валиден, интеграция ожидает MATCH/PRICING/SCORING |
| DEVOPS-001a | Черновик Docker Compose (backend+postgres) | devops | — | DONE | compose config и git diff --check пройдены; Dockerfile/образ остаются отдельной частью DevOps-001 |
| BACKEND-001 | Gradle skeleton, модули, ArchUnit | backend | ARCH-001 | DONE | review findings устранены; namespace соответствует архитектуре; `./gradlew clean test build` прошёл |
| DATA-001 | Схема, Flyway V1, индексы | data-engineer | ARCH-001 | DONE | review findings устранены; V1 применена на PostgreSQL 16, M:N и дедуп smoke test пройдены |
| BACKEND-002 | Entities, repositories | backend | BACKEND-001, DATA-001 | DONE | reviewer finding resolved: MarketPrice stores JSONB outlier IDs; clean Gradle build and PostgreSQL V1 smoke pass |
| INGEST-001 | ListingSource, MockListingSource, pipeline | ingestion | BACKEND-002 | DONE | review findings по гонкам исправлены; конкурентные тесты, история/удаление и повторный импорт проходят |
| INGEST-002 | Avito HTML parser (без HTTP) | ingestion | INGEST-001 | READY_FOR_REVIEW | Files Changed: `build.gradle`; `src/main/java/ru/avitodeals/ingestion/infrastructure/avito/*`; `src/test/java/ru/avitodeals/ingestion/infrastructure/avito/AvitoHtmlParserTests.java`; `avito-deals/docs/development/avito-parser.md`. Tests: feed/item fixtures, malformed/empty HTML, missing price, relative/exact dates, metadata, privacy |
| INGEST-003 | Публичный Avito fetcher и источник для каталога | ingestion | INGEST-002 | READY_FOR_REVIEW | Files Changed: `src/main/java/ru/avitodeals/ingestion/infrastructure/avito/*`, `MockListingSource.java`, `application.yml`, `compose.yaml`, `.env.example`, frontend listings, Avito run docs. Tests: HTTP policy/status/redirect/size, feed mapping/pagination/limits/privacy, default source wiring; `./gradlew clean test build`, 7 frontend tests/build, Compose config and diff check pass |
| MATCH-001 | Normalizer + AttributeExtractor | matching | BACKEND-001, QA-001 | DONE | reviewer findings fixed: negated status and conflicting variants are conservative; regression tests pass |
| MATCH-002 | Classifier + Matcher | matching | MATCH-001, INGEST-001 | DONE | Switch OLED alias added; product and listing-match writes serialized; regression and concurrent persistence tests pass |
| PRICING-001 | MarketPriceService | pricing | MATCH-002 | DONE | configurable LOW/MEDIUM/HIGH sample thresholds; 3/4/5+ sample tests and golden calculations pass |
| SCORING-001 | DealScore, SuspicionScore, explanation | scoring | PRICING-001 | DONE | review findings addressed; snapshot is atomic input, UNMATCHED blocks, discount precision widened; clean tests pass |
| MONITOR-001 | Scheduler, searches, pipeline | backend | SCORING-001 | DONE | review approved: durable claims serialize runs; partial ingestion cannot mark listings missing; clean build passes |
| NOTIFY-001 | NotificationService + Telegram | notifications | MONITOR-001 | DONE | review approved; application port, Telegram env config, after-commit DealScored event, persistent dedup/retry/failure state, blocked/review gate; V2/V3 unique; clean build passes |
| API-001 | REST API + OpenAPI | backend | SCORING-001 | DONE | review approved; CRUD searches, listings/deals/stats, validated pagination, persisted deal details, OpenAPI `/v3/api-docs`; 113 tests/build pass |
| FRONT-001 | Dashboard, страница сделки | frontend | API-001 | DONE | review approved after proxy/config and truthful API-status fixes; 6 tests and production build pass |
| SEC-001 | Security review | security | API-001, FRONT-001 | DONE | security-review: подтверждённых уязвимостей не найдено |
| PERF-001 | Performance/query audit | data-engineer | MONITOR-001 | DONE | review approved; deal detail использует latest-score DB query для одного listing; статические ограничения/риски документированы; clean build pass |
| DEVOPS-001 | Compose, CI/CD, health, бэкапы | devops | SEC-001 | DONE | review approved; loopback Compose, multi-stage non-root images, readiness, CI, safe backup/restore; Compose config/build/tests pass |

## Задачи по умолчанию проходят gate: Developer -> reviewer -> qa -> orchestrator.

## Пример карточки
```yaml
task:
  id: PRICING-001
  title: Implement MarketPriceService
  owner: pricing
  dependencies: [MATCH-002]
  acceptance_criteria:
    - median, p25/p75, outlier detection, confidence
    - минимальная цена не используется как рыночная
    - unit tests >= 90%
  status: BLOCKED
```

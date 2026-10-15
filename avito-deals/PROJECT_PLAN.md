# PROJECT_PLAN

## Цель
Production-ready MVP: сервис находит объявления Avito, цена которых существенно ниже рыночной, объясняет почему, оценивает риск и уведомляет через Telegram; есть REST API и React-дашборд.

## Допущения (подлежат подтверждению)
- ТЗ продукта не предоставлено отдельным файлом; план построен на доменных требованиях из задачи на систему агентов.
- Gradle (Groovy DSL), Java 21, Spring Boot, PostgreSQL, Flyway. Frontend: Vite + React + TS (ADR-005).
- Модульный монолит, без Kafka/Redis/микросервисов.
- Разработка на `MockListingSource`; реальный Avito-адаптер только в рамках ToS/легальных способов доступа.

## Состав агентов
Полный набор из 15 ролей описан в `.claude/agents/`. На MVP активны по фазам (см. TASK_BOARD): orchestrator, architect, backend, data-engineer, ingestion, matching, pricing(+scoring), reviewer, qa, docs сразу; notifications, frontend, security, devops — с соответствующих фаз.

## Фазы
| # | Фаза | Ответственные | Результат |
|---|---|---|---|
| 1 | Architecture | architect | ARCHITECTURE, ADR, database-design, module-boundaries, contracts |
| 2 | Backend + Data | backend, data-engineer | skeleton, Postgres, Flyway, entities, repositories |
| 3 | Ingestion | ingestion | ListingSource, MockListingSource, pipeline |
| 4 | Matching | matching | normalizer, extractor, classifier, matcher |
| 5 | Pricing | pricing | MarketPriceService, статистика, confidence |
| 6 | Scoring | scoring | DealScore, SuspicionScore, explanation |
| 7 | Monitoring | backend | scheduler, searches, pipeline |
| 8 | Notifications | notifications | Telegram |
| 9 | REST API | backend | API + OpenAPI |
| 10 | Frontend | frontend | dashboard, страница сделки |
| 11 | QA + Security + Perf | qa, security, data-engineer | отчёты, фиксы |
| 12 | DevOps | devops | compose, CI/CD, health, бэкапы |

## Milestones
- **M1 — Vertical slice на моках (Phases 1–6):** объявление -> product -> market price -> explainable DealScore. Критерий: корректно отличает реальную выгоду от «на запчасти», от другой модификации и от выброса.
- **M2 — Автономный мониторинг (7–9):** scheduler, Telegram, REST API.
- **M3 — MVP (10–12):** frontend, QA/security, Docker Compose, production readiness.

## Риски
| Риск | Митигация |
|---|---|
| Ложные сделки (дефект, другая модификация, аксессуар) | golden dataset QA-001, риск-флаги, порог confidence |
| Ошибки matching | консервативный матчинг, версия матчера, ручная проверка |
| Доступ к источнику (ToS, лимиты) | `ListingSource`, Mock, никаких обходов защиты |
| Малая выборка для цены | минимальный sampleSize, иначе LOW_CONFIDENCE и без скора |
| Раздувание архитектуры | ADR на каждую новую инфраструктуру |

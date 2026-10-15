# DealRadar test plan

## Current automated checks

- Backend: canonical Maven Wrapper/JUnit suite; the retained Gradle compatibility build also runs the suite.
- PostgreSQL migrations: Testcontainers applies the Flyway migrations to PostgreSQL 16 and verifies core tables; the test skips only when Docker is unavailable.
- Frontend: Vitest and Testing Library; run with `cd frontend && npm test`.
- Frontend production/type check: `cd frontend && npm run build`.
- CI: `.github/workflows/ci.yml` uses Maven Wrapper and npm.

## Coverage required before MVP completion

1. Product normalization, attribute extraction, category-specific matching and false-positive controls; report precision/recall only on a sufficiently large labeled set.
2. Market statistics: small/no samples, duplicates, conditions/currency, real low prices, outliers, confidence, freshness and deterministic rounding.
3. BigDecimal profitability: all expense components, ROI denominator, no double counting, negative profit, break-even, maximum buy price and pessimistic/base/optimistic scenarios.
4. Risk gate and ranking: critical risk cannot be offset by ROI; unknown fields and stale/low-quality data reduce confidence.
5. Ingestion: idempotency, duplicate source IDs, price changes, partial item failure, retry bounds, 403/429/5xx handling, timeout and parser errors.
6. PostgreSQL integration: Flyway on PostgreSQL, repeated import, snapshot persistence and restart recovery. Use Testcontainers if Docker is available.
7. HTTP: validation, error shape, limits, pagination and source/status/job endpoints.
8. UI: load/error/empty/stale states, sorting/filtering, detail view, cost edits, watchlist, and persistence.
9. E2E: Compose startup, import deterministic fixture, inspect listing and valuation, edit costs, save to watchlist, restart and verify persistence.

No automated test should contact Avito. A live-source smoke test must be an explicit, low-volume operator action and must stop immediately on 403/429/CAPTCHA.

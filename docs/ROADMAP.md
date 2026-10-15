# DealRadar roadmap

## P0 — verify and stabilize existing MVP

- [x] Verify ingestion-to-match-to-price-to-score event flow; added post-commit analysis in an isolated transaction and an H2 persistence/source-isolation integration test.
- [x] Persist source provenance (`REAL`, `IMPORTED`, `DEMO`), expose it in the API/UI, and include it in comparable filtering. CSV/JSON import and verification of permitted real-source access remain outstanding.
- Add CSV/JSON import with strict file size, schema, URL and field validation.
- Add a broad, configurable electronics search taxonomy that does not require a model list; do not represent this as available from Avito unless an allowed source supports it.
- Add explicit insufficient-data and data freshness semantics to market estimates.

## P1 — transparent unit economics and product workflow

- Persist cost inputs, calculation version, currency, provenance and explanation; connect the saved calculation to deal detail and ranking without confusing a market discount with ROI.
- Integrate critical-risk gates and observable liquidity/confidence factors into ranking.
- Add watchlist, settings, search-job and source-status APIs and screens.

## P2 — operational readiness

- Exercise Compose with PostgreSQL, backup/restore, readiness, restart durability and graceful job shutdown.
- Add PostgreSQL Testcontainers coverage and browser E2E tests.
- Add security review, dependency vulnerability checks and documented deployment controls.
- Reconcile the requested Maven/Spring Boot 3.x stack with the existing Gradle/Spring Boot 4.x build using a tested migration.

## External dependency

Real Avito collection is blocked in the current environment by HTTP 429. Use official API access, a licensed integration, or user-supplied exports only after their availability and terms are verified. Do not bypass the restriction.

# DealRadar MVP acceptance criteria

Statuses are based on repository inspection and checks performed on 2026-10-15.

| Criterion | Status | Evidence / remaining work |
|---|---|---|
| Java backend compiles/tests on Java 21 | Implemented and verified | `./mvnw verify` succeeded with 150 tests, including PostgreSQL migration integration and H2 ingestion-to-analysis integration; Gradle compatibility tests also pass |
| Maven and Maven Wrapper | Implemented and verified | Maven Wrapper pins Maven 3.9.16; CI and Docker use the Maven build |
| Spring Boot 3.x | Implemented and verified | Maven and retained Gradle build both pin Spring Boot 3.5.16 |
| React frontend production build | Implemented and verified | `cd frontend && npm run build` succeeded |
| Frontend tests | Implemented and verified | `cd frontend && npm test`: 5 files, 10 tests passed |
| PostgreSQL schema and Flyway migrations | Implemented and migration-tested | PostgreSQL 16 Testcontainers started and Flyway applied all three migrations; full Compose runtime after build migration remains unverified |
| Local Compose stack | Implemented; full startup not verified here | Compose defines services and health checks |
| Ingestion-to-analysis pipeline | Implemented and integration-tested on H2 | New/changed listings trigger persisted matching, market valuation, and scoring after commit; later listings re-score same-product peers; active comparables require the same source ID, currency, and condition. PostgreSQL event-flow integration and explicit source provenance remain outstanding |
| Permitted real Avito data retrieval | Blocked by external restriction | Avito returned HTTP 429; no listing was retrieved; permissions/API terms not established |
| CSV/JSON user import | Not implemented | No source adapter or API import route found |
| Source provenance REAL/IMPORTED/DEMO and enforced isolation | Partially implemented | Analysis groups by exact adapter/source ID; provenance categories and policy enforcement remain absent |
| General electronics classification and conservative matching | Partially implemented | Deterministic category/matching components and tests exist; no broad quality measurement claimed |
| Market median and quartiles with robust outlier handling | Implemented and unit-tested | `MarketPriceCalculator`; sample-size confidence is LOW/MEDIUM/HIGH |
| Insufficient-data status, trimmed mean, spread, freshness | Not implemented | Current result schema does not expose these requested metrics |
| Cost-based net profit, ROI, scenarios, break-even | Implemented and unit/API/frontend-client tested; not persisted or used in deal ranking | `/api/profitability/calculate` uses BigDecimal; deal-detail UI accepts explicit costs and shows P25/median/P75 scenarios |
| Risk and deal scoring | Partially implemented | Persisted scores/explanations exist; factors and critical-risk gate need full requirement coverage |
| Watchlist, settings, source/job status APIs | Not implemented | Current API is limited to searches, listings, deals, stats and health |
| Authentication for public deployment | Not implemented | Local single-user restriction documented |
| Price history persistence | Implemented in schema and current services | End-to-end live behavior/container restart not verified in this task |
| Automated E2E browser scenario | Not implemented/verified | No browser executable or Playwright E2E suite identified |
| Configured-location filtering of Avito feed cards | Implemented and regression-tested | Feed rows whose URL region slug does not match selected location are skipped; transliteration is deterministic |

“Implemented” means code/tests exist; it does not mean all external integrations or full user journeys have been independently validated.

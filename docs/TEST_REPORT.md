# Test report

Date: 2026-10-15.

## Checks run

| Check | Result |
|---|---|
| Maven canonical backend build | PASS — `./mvnw --batch-mode --no-transfer-progress verify` on Java 21; Maven 3.9.16, Spring Boot 3.5.16, compiler release 21; 151 tests passed, 0 failures/errors/skips. |
| PostgreSQL migration integration test | PASS — Testcontainers started PostgreSQL 16, applied V1–V3, inserted legacy Avito/mock listings, then applied V4 and verified the backfilled `REAL`/`DEMO` values and provenance column. |
| Ingestion-to-analysis application integration | PASS — H2-backed Spring application test persisted product matches, same-source market valuations and deal scores; a later listing rescored an earlier peer, and a separate demo source did not affect Avito-source prices. This validates application wiring, not PostgreSQL transaction behavior. |
| Gradle compatibility backend tests | PASS — `./gradlew --no-daemon test`; full test suite passed after Spring Boot 3 alignment. |
| Frontend unit tests | PASS — `cd frontend && npm test`; 5 files, 10 tests passed. |
| Frontend production/type build | PASS — `cd frontend && npm run build`. |
| Manual search execution | PASS — focused Java tests cover `POST /api/searches/{id}/run`, immediate claim regardless of schedule interval, and ingestion report; frontend: 5 files, 11 tests pass and production/type build succeeds. The endpoint was exercised with the isolated mock source, not a live Avito request. |
| Maven backend image build | PASS — `docker build --tag dealradar-backend:validation --file Dockerfile .`; no service was started. |
| Compose build and PostgreSQL-backed startup | PASS before the Maven/Spring Boot 3 migration — PostgreSQL, backend and frontend became healthy; Flyway-backed readiness and API stats responded. |
| Profitability API smoke test | PASS before the Maven/Spring Boot 3 migration — returned net profit `235.00 RUB`, ROI `25.97%`, break-even, purchase limit and all scenarios. |
| Frontend HTTP smoke test | PASS before the Maven/Spring Boot 3 migration — `http://127.0.0.1:5173/` returned HTTP 200. No browser interaction was performed. |
| Full Compose runtime after Maven migration | NOT RUN — existing active searches may make source requests on startup; backend image build and PostgreSQL migration test were run instead. |
| Avito reachability | BLOCKED — an actual scheduled request received HTTP 429 with a one-minute cooldown; subsequent searches were paused. No listing was retrieved. Official API/terms remained inaccessible (HTTP 429). |
| PostgreSQL restart/backup recovery | NOT RUN. |
| Browser E2E | NOT RUN; no browser runtime or Playwright E2E suite was available. |

After the smoke test the temporary Compose services were stopped with `docker compose stop` (the PostgreSQL volume was preserved) to avoid additional source requests from already-active saved searches. The local Compose command starts the services again. Backend compiler emitted an unchecked-operations note in the existing `AvitoHttpPageFetcherTests`; React Router emitted v7 migration warnings during frontend tests.

The H2 integration test verifies the ingestion → matching → pricing → scoring application flow with isolated fixtures. It does not verify the same flow against PostgreSQL or prove real Avito collection.

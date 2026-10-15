# Test report

Date: 2026-10-15.

## Checks run

| Check | Result |
|---|---|
| Maven canonical backend build | PASS — `./mvnw --batch-mode --no-transfer-progress verify` on Java 21; Maven 3.9.16, Spring Boot 3.5.16, compiler release 21; 144 tests passed. |
| PostgreSQL migration integration test | PASS — Testcontainers started PostgreSQL 16, Flyway applied all 3 migrations, core tables were verified; 1 test, 0 failures, 0 skips. |
| Gradle compatibility backend tests | PASS — `./gradlew --no-daemon test`; full test suite passed after Spring Boot 3 alignment. |
| Frontend unit tests | PASS — `cd frontend && npm test`; 5 files, 10 tests passed. |
| Frontend production/type build | PASS — `cd frontend && npm run build`. |
| Maven backend image build | PASS — `docker build --tag dealradar-backend:validation --file Dockerfile .`; no service was started. |
| Compose build and PostgreSQL-backed startup | PASS before the Maven/Spring Boot 3 migration — PostgreSQL, backend and frontend became healthy; Flyway-backed readiness and API stats responded. |
| Profitability API smoke test | PASS before the Maven/Spring Boot 3 migration — returned net profit `235.00 RUB`, ROI `25.97%`, break-even, purchase limit and all scenarios. |
| Frontend HTTP smoke test | PASS before the Maven/Spring Boot 3 migration — `http://127.0.0.1:5173/` returned HTTP 200. No browser interaction was performed. |
| Full Compose runtime after Maven migration | NOT RUN — existing active searches may make source requests on startup; backend image build and PostgreSQL migration test were run instead. |
| Avito reachability | BLOCKED — an actual scheduled request received HTTP 429 with a one-minute cooldown; subsequent searches were paused. No listing was retrieved. Official API/terms remained inaccessible (HTTP 429). |
| PostgreSQL restart/backup recovery | NOT RUN. |
| Browser E2E | NOT RUN; no browser runtime or Playwright E2E suite was available. |

After the smoke test the temporary Compose services were stopped with `docker compose stop` (the PostgreSQL volume was preserved) to avoid additional source requests from already-active saved searches. The local Compose command starts the services again. Backend compiler emitted an unchecked-operations note in the existing `AvitoHttpPageFetcherTests`; React Router emitted v7 migration warnings during frontend tests.

These checks verify the calculator and local infrastructure, not the complete ingestion → matching → pricing → scoring workflow or real Avito listing collection.

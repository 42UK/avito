# DealRadar requirements and current behavior

## Functional requirements

1. **Discover and ingest listings:** support bounded, repeatable searches, pagination, deduplication, and price observations. The current implementation has saved searches, a scheduled ingestion pipeline, and a public-page adapter. Committed new or repriced listings trigger matching, market-price snapshot persistence, and deal scoring; later same-product listings also trigger peer rescoring. It does not yet discover all electronics categories without configured query terms.
2. **Source handling:** represent the source and status explicitly; stop on access denial and respect server rate limiting. The current Avito adapter has bounded requests and a cooldown on HTTP 429. Reachability was not established in this environment (HTTP 429). Official API or licensed access has not been verified.
3. **Product resolution:** normalize names, extract category/attributes, match known variants conservatively, and retain uncertainty. The current implementation has deterministic classifiers, extractors, normalizers and matchers; unknown items are not evidence of high match quality.
4. **Market estimate:** use active, same-product, same-condition, same-currency comparables and robust statistics. The current calculation persists median, P25/P75, confidence, outlier IDs and method. It does not persist the requested trimmed mean, price spread, data age, or a distinct insufficient-data state.
5. **Profitability:** compute net profit and ROI using acquisition and explicit costs with decimal arithmetic. A stateless BigDecimal service and API now calculate net profit, ROI, break-even, maximum purchase price and P25/median/P75 scenarios. Results are not persisted or integrated into the saved deal score.
6. **Ranking:** explain score and risk signals; a high return must not hide critical risk. Existing scoring stores score, risk, confidence, flags and explanations; coverage of all requested factors is incomplete.
7. **User interface:** show searches, listings, deal details, health/errors, and empty/loading/error states using the API. Existing React app has search/listing/deal/stat views; the full settings/import/watchlist/jobs/source-status experience is incomplete.
8. **Persistence and operations:** PostgreSQL, migrations, local health checks, backups, repeatable startup. These are present in the current Gradle/Compose implementation; restore and full container startup remain to be exercised in this task.

## Non-functional requirements

- Java 21 runtime target; money uses `BigDecimal`.
- PostgreSQL is the persistent store; no production H2 substitute.
- Bound page size, source request time, response size, page count and query interval.
- No unrestricted user-supplied fetch URL; never follow listing links during ingestion.
- Keep credentials out of Git and logs; do not expose the local API to a public interface without authentication.
- Tests must be deterministic and must not call Avito.

## Source and data semantics

The `listing.data_origin` field stores `REAL`, `IMPORTED`, or `DEMO`; source adapters declare their origin, and the listing API exposes it. The analysis pipeline compares only records with the same source ID and origin, as well as matching currency and condition. Legacy `avito` rows are migrated as `REAL`; all other legacy sources default to `DEMO`. `REAL` describes adapter provenance, not verified access permission or a verified completed collection. CSV/JSON import is not implemented yet; do not treat `mock` or demo rows as market evidence.

# DealRadar data model

The current Flyway baseline is `src/main/resources/db/migration/V1__initial_schema.sql`; subsequent migrations are versioned in the same directory.

Current persisted concepts include:

| Entity/table | Purpose |
|---|---|
| `search` | User-configured query, JSON filters, interval, score threshold and active flag |
| `listing` | Source-scoped unique external listing, title, price/currency, condition, URL, status, first/last observation |
| `listing_price_history` | Listing price observations over time |
| `search_listing` | Association and observation range between saved search and listing |
| `product`, `product_attribute` | Canonical product identity and extracted attributes |
| `listing_product_match` | Match status, confidence and matcher version |
| `market_price` | Market snapshot, sample size, confidence, method and outlier identifiers |
| `deal_score` | Score/risk/confidence and explanation/flag arrays |
| `notification` | Delivery deduplication and state |

PostgreSQL uses UUID keys, `numeric` monetary columns, foreign keys, checks, indexes and JSONB for bounded supplemental data.

## Important gaps from the target model

- `listing.source` is not a normalized `REAL`/`IMPORTED`/`DEMO` origin enum; market queries cannot reliably enforce the requested provenance boundary.
- There is no persistent cost-analysis/scenario model for logistics, repair, fees, risk reserve, break-even or ROI. The current calculator is stateless.
- The target tables for watchlist, configuration, ingestion jobs, source status and system events are not all present in the current schema.
- `market_price` lacks trimmed mean, dispersion, observation age and a dedicated insufficient-data value.
- Current deal detail comparables are a current query set, not necessarily the exact historical set used for the saved price snapshot (see `avito-deals/docs/api/api-contract.md`).

## Migration policy

Add forward-only Flyway migrations; do not edit an already-applied migration. Preserve existing listing IDs and price history when adding provenance or analysis records. Add DB constraints and indexes that match application invariants.

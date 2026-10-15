# DealRadar architecture

## Current implementation

The repository is a single Java modular monolith with PostgreSQL persistence and a separate React/Vite frontend. Current Java packages separate `api`, `listing`, `ingestion`, `product`, `pricing`, `scoring`, `search`, `notification`, and `shared` responsibilities.

The current ingestion-to-analysis pipeline is:

```text
saved search -> ListingSource -> ingestion/persistence -> after-commit event
             -> isolated analysis transaction -> product matching
             -> market-price snapshot -> deal score -> API -> React UI
```

`ListingIngested` and `ListingPriceChanged` are handled after the ingestion transaction commits. Analysis runs in a new transaction and persists matching, valuation, and score results; new matching listings also trigger peer rescoring. Comparables are constrained to active listings with the same adapter/source ID, data origin, currency, and extracted condition. An H2 application integration test verifies persistence and comparable isolation; PostgreSQL integration verifies the migration and legacy-origin backfill, not the complete event flow.

The public Avito adapter declares origin `REAL`, and the mock adapter declares `DEMO`; other adapters default to `DEMO` unless they explicitly declare an origin. This provenance label does not confirm source permission or successful collection. The mock/demo adapter is not a real source.

## Runtime and build

The canonical backend build is Maven Wrapper 3.9.16, Spring Boot 3.5.16, and Java 21. The previous Gradle 9.7.1 build is retained as a compatibility path and aligned to Spring Boot 3.5.16. The frontend uses React 18, TypeScript, Vite, and React Router. Compose defines PostgreSQL, backend and frontend services. See [environment audit](ENVIRONMENT_AUDIT.md).

## Boundaries and constraints

- Controllers translate HTTP requests to application services; they should not own pricing or matching rules.
- JPA repositories and Flyway schema are persistence concerns.
- Source adapters implement the ingestion source contract.
- Product matching and market-price estimation are separate deterministic components.
- Deal scoring is distinct from price estimation; an asking-price discount is not net resale profit.
- Authentication is absent; the supported deployment target is a trusted local machine.

## Deferred architecture

Do not add a broker or microservices for the single-user MVP. Add source provenance, imports, persisted cost analysis, and complete job/status endpoints before expanding operational complexity. Maven/Spring Boot 3 migration is complete; avoid diverging the retained Gradle compatibility build.

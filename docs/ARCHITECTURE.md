# DealRadar architecture

## Current implementation

The repository is a single Java modular monolith with PostgreSQL persistence and a separate React/Vite frontend. Current Java packages separate `api`, `listing`, `ingestion`, `product`, `pricing`, `scoring`, `search`, `notification`, and `shared` responsibilities.

The intended current pipeline is:

```text
saved search -> ListingSource -> ingestion/persistence -> product matching
             -> market-price snapshot -> deal score -> API -> React UI
```

The public Avito adapter is an optional source, not a claim of successful collection. It does not bypass source protections. The mock adapter is not a real source. Ingestion and evaluation behavior must be verified against the deployed code, not inferred from the diagram.

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

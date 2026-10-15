# DealRadar API — current contract and gaps

The current API is rooted at `/api`; pagination is zero-based and page size is limited to `1..100`. Responses use JSON and errors use Spring `ProblemDetail`.

| Method | Path | Current behavior |
|---|---|---|
| GET | `/api/searches` | Paginated saved searches |
| POST | `/api/searches` | Create a saved search |
| GET/PUT/DELETE | `/api/searches/{id}` | Read, replace, or remove a search |
| GET | `/api/listings` | Paginated stored listings |
| GET | `/api/listings/{id}` | Read one listing |
| GET | `/api/deals` | Paginated eligible persisted deal scores |
| GET | `/api/deals/{id}` | Deal, associated listing, market snapshot, current comparables and history |
| GET | `/api/stats` | Search, listing and eligible-deal counts |
| POST | `/api/profitability/calculate` | Stateless BigDecimal calculation using explicit purchase/resale assumptions and costs; returns net profit, ROI, break-even, purchase limit and three scenarios |
| GET | `/actuator/health` | Spring Boot health |
| GET | `/v3/api-docs` | OpenAPI document |

Swagger UI is not currently included. Search creation is documented in `avito-deals/docs/api/api-contract.md`.

Not yet implemented as specified: import endpoints, listing comparables/analysis subresources, manual search-run/job endpoints, watchlist, settings, source status, event feed, and explicit analysis recalculation.

The profitability endpoint does not persist the request or connect the calculation to the saved deal score. Its current caller is the deal-detail calculator; cost entries must be re-entered after leaving the page.

There is no API authentication. Bind locally only. Do not expose it to a network without adding and verifying an authentication/authorization mechanism.

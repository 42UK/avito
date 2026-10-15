# REST API contract (API-001)

Base path: `/api`. Responses and requests use JSON. Collection responses use
`{content, page, size, totalElements, totalPages}` with zero-based pages; `size`
must be in `1..100` (default `20`).

## Endpoints

| Method | Path | Behavior |
|---|---|---|
| GET | `/searches?page=&size=` | Paginated saved searches |
| POST | `/searches` | Create a search; returns `201` and `Location` |
| GET | `/searches/{id}` | Read a saved search |
| PUT | `/searches/{id}` | Replace all editable search fields |
| DELETE | `/searches/{id}` | Delete a search; returns `204` |
| GET | `/listings?page=&size=` | Paginated stored listings, newest `lastSeenAt` first |
| GET | `/listings/{id}` | Read one listing |
| GET | `/deals?page=&size=` | Latest saved score per listing, only `DEAL` decisions for active listings |
| GET | `/deals/{id}` | Read a saved deal score and its listing, explanation, market snapshot, comparables, outliers, and price history |
| GET | `/stats` | Counts of searches, active searches, listings, active listings, and eligible deals |

Search creation/update JSON:

```json
{
  "name": "Pixel 8",
  "query": "Google Pixel 8",
  "filters": {"priceMin": 10000, "priceMax": 50000, "location": "Москва", "category": "Телефоны"},
  "intervalMinutes": 60,
  "minDealScore": 40,
  "active": true
}
```

Search interval is limited to `1..10080` minutes, `minDealScore` to `0..100`
with at most two fractional digits, and `priceMin`/`priceMax` must be
non-negative numbers in ascending order. All six search request fields are
required except `filters`, which defaults to an empty object.
`name` is limited to 500 characters and `query` to 5000. Unknown search-filter
keys, null filter values, and non-string location/category values are rejected.

## Deal semantics

`/deals` does not calculate deals on request and never infers a deal from an
asking price. A result is returned only when a saved `DealScore` contains the
`DEAL` decision, is the latest score for its listing, and the listing is still
active. Review, blocked, no-deal, stale, and removed-listing scores are omitted.
The path ID is the saved `DealScore` ID.

The response includes the persisted market-price snapshot and its saved
`outlierIds`. The schema stores outlier IDs but not the complete comparable
membership at calculation time. Consequently `comparables` in deal details is
explicitly labeled `CURRENT_MATCHED_LISTINGS`: it is the current active set of
matched listings with the same product, condition, and currency, not a
historical reconstruction of the exact calculation sample. Price history is
the persisted sequence for the candidate listing.

## HTTP errors

- `400 Bad Request`: malformed JSON/UUID, invalid search fields, page, or size.
- `404 Not Found`: unknown search/listing/deal ID. Deals that no longer satisfy
  the eligibility rules are also hidden as `404`.
- `201 Created`: successful search creation, with a `Location` header.
- `204 No Content`: successful search deletion.
- `200 OK`: successful reads and replacement.

Errors use Spring's `ProblemDetail` JSON representation. This MVP API adds no
authentication or authorization; those are a subsequent security phase.

OpenAPI JSON is served at `/v3/api-docs` and Swagger UI at
`/swagger-ui/index.html` by `org.springdoc:springdoc-openapi-starter-webmvc-ui`
2.8.17, compatible with Spring Boot 3.5.x.

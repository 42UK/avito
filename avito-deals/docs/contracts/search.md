# Contract: Search
Status: Draft
Search{id, name, query, filters{priceMin,priceMax,location,category}, intervalMinutes, minDealScore, active}.
The scheduler persists `lastRunAt` for each search. A search is due when it has never run
or its `intervalMinutes` have elapsed since `lastRunAt`; failures are retried no sooner
than the same interval. Search results are partial snapshots and never imply listing removal.
REST: `/api/searches` supports GET (paginated list), POST (create), and
`/api/searches/{id}` supports GET, PUT (full replacement), and DELETE. Search
requests use `name`, `query`, `filters`, `intervalMinutes`, `minDealScore`, and
`active`. Filters currently accept `priceMin`, `priceMax`, `location`, and
`category`.

See [API contract](../api/api-contract.md) for response envelopes, remaining
read endpoints, status codes, and snapshot semantics.

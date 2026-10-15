# DealRadar MVP — Product requirements

**Status:** implementation in progress; public Avito collection is not verified.

## Product goal

Help a single local user discover electronics listings that may be undervalued, inspect relevant market comparables, and understand the uncertainty and costs behind a resale opportunity. A displayed asking price is not evidence of a completed sale or guaranteed profit.

## Users and primary flow

The MVP is for one local operator. The current flow is to configure a saved search, let the configured source ingest its results, inspect stored listings and any persisted deal analyses, and review search/source errors. Public-page collection is optional and must stop when the source denies access. Deterministic fixtures are for tests only.

## Scope

Existing capabilities include a modular Java backend, PostgreSQL schema/migrations, saved-search scheduling, listing ingestion, deterministic product classification/matching, robust market-price statistics, deal scoring, a React catalog/search UI, and local Compose configuration.

The following user requirements remain incomplete: source-independent CSV/JSON import, broad category discovery without user-provided search phrases, explicit REAL/IMPORTED/DEMO provenance, persistence/integration of user cost assumptions into saved deal analyses, a complete sources/jobs/watchlist/settings API, and a verified permitted Avito integration. A stateless cost-based profitability calculator is available in deal details. See [acceptance criteria](ACCEPTANCE_CRITERIA.md) and [roadmap](ROADMAP.md).

## Product principles

- Never label mock/imported data as live Avito data.
- Keep source restrictions and uncertainty visible.
- No CAPTCHA, access-control, rate-limit, or anti-bot circumvention.
- Explanations and input samples accompany market estimates and scores.
- Unknown model or condition information remains unknown rather than being inferred as a match.
- The service is local-only and single-user until real authentication and deployment hardening are implemented.

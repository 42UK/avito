# Security review — local MVP scope

**Status:** initial repository/configuration review; not a penetration test.

## Findings

| Severity | Finding | Status |
|---|---|---|
| HIGH | API has no authentication or authorization. This is acceptable only for a trusted local single-user run; do not expose ports to a network. | Open; documented limitation |
| HIGH | Avito access was not verified and returned HTTP 429. Do not retry aggressively, bypass access restrictions, or treat adapter presence as permission. | External restriction; adapter documents stop/cooldown behavior |
| MEDIUM | Required REAL/IMPORTED/DEMO provenance is absent, so application-level separation of demo/imported/live data is not enforceable. | Open |
| MEDIUM | Required user URL/CSV/JSON import surfaces are not implemented. If added, validate uploads/URLs and reject local, private, link-local and metadata-service destinations; prefer no arbitrary URL fetching. | Open; not currently an exposed import feature |
| LOW | `.env` exists and is ignored by Git; secret contents were not inspected. `.env.example` is present. | Verify locally with secret scanning before release |

## Existing controls observed

- Compose binds published service ports to loopback.
- `.env` is Git-ignored; Compose reads secrets from environment.
- Current public-page adapter documentation specifies HTTPS to Avito, no proxy/browser disguise, no CAPTCHA bypass, response size/time/page limits, and stopping/pausing on 403/429.
- PostgreSQL uses parameterized/JPA access and schema constraints in the current implementation.

## Before network deployment

Implement real authentication/authorization, review CORS and request limits, secure cookies/headers/TLS, add audit logging with redaction, run dependency scanning, and test backup/restore. Re-review SSRF and upload handling when import capabilities are added.

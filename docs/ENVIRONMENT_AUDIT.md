# Environment audit

Audit date: 2026-10-15

## Repository and existing work

- Repository: `42UK/avito`, branch `dev`.
- The repository already contains a substantial Java/Spring backend, a React/Vite frontend, PostgreSQL migrations, Compose configuration, backup scripts, and project documentation under `avito-deals/docs/`.
- The requested `MarketPriceService` is present at `src/main/java/ru/avitodeals/pricing/application/MarketPriceService.java`.
- The worktree was already dirty before this task: tracked staged/unstaged files and many untracked application files were present. Those files are existing work and must be preserved; task changes should be additive or narrowly targeted.
- `.env` exists and is Git-ignored. Its contents and environment variable values were not inspected or recorded. `.env.example` is present.

## Tooling

| Capability | Result |
|---|---|
| Java | Java 23 is active; Java 21.0.12 is installed and can be selected. |
| Maven | At audit start `mvn` and Maven Wrapper were absent. Maven 3.10.0 was later installed locally to generate the repository Maven Wrapper, pinned to Maven 3.9.16. |
| Java build | The existing Gradle Wrapper (Gradle 9.7.1) was preserved and aligned with Spring Boot 3.5.16. The new canonical build is Maven Wrapper; `./mvnw clean verify` passes. |
| Node.js / npm | Node.js 24.13.0 and npm 11.6.2 are available. Frontend dependencies are already present under `frontend/node_modules`. |
| Frontend build | Frontend is a separate Vite application under `frontend/`; frontend commands must run from that directory. |
| Docker | Docker 29.7.2 and Compose 5.5.0 are installed; the Docker daemon responded. |
| Shell / process execution | Shell commands and process execution are available. |
| Browser | No Chromium, Chrome, or Playwright executable was found on `PATH`; no browser-based E2E run has been established. |
| IDE / MCP | IntelliJ IDEA MCP tools are available. `.mcp.json` lists the `idea` server; no browser MCP integration was identified. |

## Network and data-source reachability

- Maven Central returned HTTP 200 from the environment.
- A request to `https://www.avito.ru/` returned HTTP 429 from the site's edge protection. A separate Avito page request also returned HTTP 429. No listing data was successfully retrieved or used in this audit.
- Fetches of `https://developers.avito.ru/` and `https://www.avito.ru/legal/rules` were also redirected/denied with HTTP 429. Official API availability and current legal/contractual terms therefore remain unverified.
- This is a point-in-time reachability observation, not evidence about the availability or contractual terms of any official Avito API or licensed integration.
- The existing adapter/documentation describes unauthenticated public-page access and stops on rate limiting. Real-world collection is not verified and must not be represented as working based only on the adapter's presence.

## Immediate consequences

- Use `./mvnw --batch-mode clean verify` as the canonical backend build. Gradle remains as a compatibility build for the existing project.
- Select Java 21 explicitly for a reproducible build.
- Run frontend checks from `frontend/`.
- Keep Avito network access disabled in tests; do not retry or bypass HTTP 429.
- Use imports or isolated fixtures for deterministic product and pricing tests.

# KMicro backend (modulith)

E-commerce backend built as a Spring Modulith: one Spring Boot app, strict modules, each one ready to become its own microservice later.

Status: scaffolded (scope feature 1 done). `/audit` adds the full conventions and tooling next.

## Stack

Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2, Maven (wrapper committed), PostgreSQL, Redis, Keycloak, Meilisearch, Mailpit. Source: `docs/specs/0001-stack-architecture/index.md` (`## Proposed stack`).

## Build approach

Tracer Bullet (prove one thin real path through every layer first, then thicken one part at a time). Source: `docs/scope/scope.md` header.

## Commands

- Needs JDK 25 (the build fails on Java 21 with a "class file version" error).
- Run: copy `.env.example` to `.env`, start Docker, then run `KmicroApplication` in IntelliJ (run config in `.run/`, Spring profile `local`), or `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`. Spring Boot starts the `core` Compose containers for you.
- The packaged jar (`java -jar`) leaves out Docker Compose support on purpose, so run locally from IntelliJ or Maven.
- Build: `./mvnw -DskipTests package`.
- Boundary test: `./mvnw test -Dtest=ModularityTests` (no Docker needed). Module docs land in `target/spring-modulith-docs/`.
- Local ports: app 8080, Postgres 55432 (`POSTGRES_PORT`), Redis 56379 (`REDIS_PORT`), Keycloak 8081, Meilisearch 7700, Mailpit SMTP 1025 and UI 8025.

## Code layout

- One package per module under `com.kmicro` (12 modules plus the open `shared` kernel). Each module's `package-info.java` lists its `allowedDependencies`; add a new arrow on purpose, never to silence the boundary test.
- Each module exposes only `api/` and `events/` (named interfaces). Everything else in the module is internal.
- Flyway: one folder per module, `src/main/resources/db/migration/<module>/`, files named `V<block>_<n>__<module>_<what>.sql`. Blocks: modulith 0, identity 1, customer 2, catalog 3, search 4, inventory 5, cart 6, ordering 7, payment 8, notification 9, settings 10, promotion 11, review 12. A new module folder must also be added to `spring.flyway.locations` and `spring.flyway.schemas` in `application.yml`.
- Config: shared settings in `application.yml`, local only settings in `application-local.yml`, secrets only from env vars.

## Source of truth

- `docs/design/backend-design.md`: every locked decision for this repo (modules, data, money, tax, flows, events, security, API, infra, testing, phases).
- `docs/design/frontend-design.md`: how the web and mobile clients use this API. Read it only when an API contract or the login flow matters.
- Read only the section you need, not the whole file.
- Decisions marked **Locked** are final. Do not reopen them. If work needs a decision the docs do not cover, stop and ask the owner. Do not assume.

## Workflow

Skills live in `.agents/skills/` (Claude Code reads them through the `.claude/skills` link).

- First time only: `/scope` (seed the scope from the design doc phases) → `/architect` (record the stack spec from the design doc) → scaffold → `/audit`.
- Per feature: `/architect` → `/develop` → `/check verify` → `/sync`.
- When `/check verify` finds wrong output: `/debug` → fix → one regression test for that bug.
- State lives in files (`docs/scope/`, `docs/specs/`, this file), so start a fresh session (`/clear`) between steps to keep context small.

## Rules

- Testing: no test driven development and no test suites written up front. Build the feature, then check it by running the real app (`/check verify`). Write a test only when the output is wrong, as a regression test for that bug. The one standing test is the Spring Modulith module boundary check.

- Phase 1 scope only, unless the owner says otherwise. Later phase work is listed in the design doc, section 16.
- No new dependency, skill, MCP server or tool install without the owner's approval. Never run `npx skills add`. Pin exact versions. Prefer versions published at least 7 days ago.
- No secrets in the repo. Config comes from environment variables; commit a `.env.example` only.
- Tooling (chosen in feature 2, installed by `/develop tooling`): Spotless with Palantir Java Format, compiler warnings as errors (`-Xlint:all -Werror`), Dependabot for dependency and image updates, plus `.editorconfig`.
- Write for the owner in short, simple English.

## Git

integration: on

- Work on a feature branch, never commit to `main` directly.
- Never push or open a PR without the owner's OK.
- Never delete a branch (local or remote). After a merge, add the branch to `docs/branches-to-delete.md`; the owner deletes them all after the final phase.

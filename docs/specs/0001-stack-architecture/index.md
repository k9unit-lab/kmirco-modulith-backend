# 0001. Spring Modulith stack and module layout for the KMicro backend

**Date**: 2026-10-30
**Status**: In Progress

## Summary

KMicro is one Spring Boot app split into strict modules (a "modulith"), on Java 25, Spring Boot 4.1 and Spring Modulith 2.1. It uses PostgreSQL, Redis, Keycloak, Meilisearch and Mailpit, all started for you by Docker Compose when you press Run in IntelliJ. Almost all of this was already locked in the design doc. This spec records it in one place, closes the open "versions" item, and settles the small scaffold choices the doc left open, so the scaffold step has nothing left to guess.

## Requirements

This is a decision spec (it records the stack, it is not a build plan). The feature that uses it is scope feature 1, "Stack & architecture". Its done line is the contract:

- The empty app starts from IntelliJ with Postgres, Redis, Keycloak, Meilisearch and Mailpit started for you (`core` Compose profile).
- The Spring Modulith boundary test passes.

`/develop` works out the scaffold steps from `## Proposed stack` below at build time.

## Decision

**Chosen option**: Option 1: Spring Modulith on the locked design doc stack (see `rationale.md`).

Build one Maven project, `com.kmicro:kmicro-backend`, on Java 25 LTS, Spring Boot 4.1.x and Spring Modulith 2.1.x. Keep one package per module under `com.kmicro`, enforce the boundaries with `ApplicationModules.verify()`, and use the infrastructure locked in `docs/design/backend-design.md`.

**Implementation skills**: none installed (community skills reviewed and not installed yet, see `## Follow-up`).

## Rationale

Reasoning and options: see [rationale.md](rationale.md).

## Proposed stack

**Platform and build**

| Layer | Choice | Reason |
|---|---|---|
| Language | Java 25 LTS | Newest long term support (LTS) release, longest support window, and virtual threads no longer get stuck on `synchronized` blocks. |
| Build | Maven, one project, Maven Wrapper (`mvnw`) committed | Locked in the design doc. The wrapper means everyone uses the same Maven version. |
| Coordinates | `groupId` `com.kmicro`, `artifactId` `kmicro-backend`, base package `com.kmicro` | Matches the repo name and the locked package. Avoids a clash with the old kmicro repo. |
| Framework | Spring Boot 4.1.x (start on 4.1.2) | Latest stable line today. 4.2 is still a release candidate (RC), so we wait for it. |
| Modularity | Spring Modulith 2.1.x (start on 2.1.2) | The Modulith line built for Boot 4.1. It checks module boundaries and keeps the event registry. |
| Concurrency | Virtual threads on: `spring.threads.virtual.enabled=true` | Cheap concurrency for blocking calls (Postgres, Redis, Keycloak, Razorpay, Meilisearch). |
| Config format | `application.yml`, plus `application-<profile>.yml` where needed | Easy to read for nested settings. Secrets come from env vars, never from the file. |
| Boilerplate | Lombok, **JPA entities only** (rules below) | Your choice. It cuts getter, setter and constructor noise on entities. Records cover DTOs and events. |
| DTO mapping | Hand written: a small static `from(entity)` or `toDto()` method per DTO | No extra library. Easy to read and debug. Fits the "few libraries" rule. |

**Data and infrastructure** (all **Locked** in the design doc unless marked)

| Layer | Choice | Reason |
|---|---|---|
| Primary DB | PostgreSQL, one database, one schema per module plus `modulith` | Locked. No joins or foreign keys across schemas, so any module can move out later. |
| Data access | Spring Data JPA (Hibernate) | The module rules are written in JPA terms. Use plain SQL (`JdbcClient` or `@Query`) for reports and complex reads. |
| Migrations | Flyway, one folder per module: `db/migration/<module>` | Locked. Wiring is described below (the data model spec, feature 3, may refine it). |
| Module events | Spring Modulith event publication registry, JDBC flavour (`spring-modulith-starter-jdbc`), table in the `modulith` schema | Locked registry. JDBC flavour is my pick (below). |
| Cache | Redis: Spring Cache, Spring Session (web login), Bucket4j rate limits | Locked. |
| Rate limits | Bucket4j `com.bucket4j:bucket4j_jdk17-lettuce` 8.20.0 | Locked library. This is its Redis (Lettuce client) module. |
| Login | Keycloak. Web: BFF (`oauth2Login` plus Redis session cookie). Mobile: OAuth2 Resource Server (Bearer) | Locked. Built in feature 4. |
| Search | Meilisearch with the official `meilisearch-java` client | Search engine locked. Client: your pick. Used from feature 6. |
| Email | Spring Mail plus Thymeleaf templates; Mailpit catches mail locally | Thymeleaf: your pick for now. Admin editable templates come later (see `## Follow-up`). |
| Files | `FileStorage` interface: local folder first, S3 (LocalStack) later | Locked. Built in feature 7. |
| Scheduled jobs | `@Scheduled` plus ShedLock `net.javacrumbs.shedlock` 7.10.1 (JDBC lock provider) | Locked (carried over from kmicro). Safe with 2 or more instances. |
| Broker | Apache Kafka 4.x (KRaft), `messaging` profile, Springwolf docs | Locked. Added in slice 11, not at scaffold. |
| API | REST under `/api/v1`, springdoc `springdoc-openapi-starter-webmvc-ui` 3.2.0, one group per module | Locked. springdoc 3 is the line for Boot 4. |
| Errors | RFC 9457 Problem Details with a stable `code` field | Locked. Built in feature 4. |
| Validation | Bean Validation (`spring-boot-starter-validation`) | Locked. |
| Local dev | IntelliJ Run with `spring-boot-docker-compose`; only the `core` profile at scaffold | Locked tool. Profile scope: your pick. |
| Observability | Micrometer plus OpenTelemetry to the existing Grafana stack, `observability` profile | Locked. Built in feature 11. |
| Testing | Only the Modulith boundary test now (`ApplicationModules.verify()` plus `Documenter`) | Locked. Regression tests only after a bug. |
| Hosting | Not decided here | Belongs to the separate deployment doc (Open). |

### Pinned versions at scaffold

Pin exact versions (`AGENTS.md`). Each one below was released before 2026-10-23, so it is at least 7 days old.

| Artifact | Version | Note |
|---|---|---|
| Spring Boot (parent) | 4.1.2 | Released 22 Oct. Moving to 4.2 is a later, separate upgrade. |
| Spring Modulith BOM | 2.1.2 | Confirm in the Modulith docs that 2.1.x is the line for Boot 4.1 before you pin it (the web check could not open a compatibility table). |
| springdoc-openapi-starter-webmvc-ui | 3.2.0 | Added when the first controller lands. |
| Lombok | 1.18.48 | Supports Java 25. |
| Bucket4j (`bucket4j_jdk17-lettuce`) | 8.20.0 | Added in the slice that adds rate limits. |
| ShedLock (`shedlock-spring` plus `shedlock-provider-jdbc-template`) | 7.10.1 | Added with the first scheduled job (order expiry). |
| meilisearch-java | latest release that is at least 7 days old | Not checked online. Pin it when feature 6 adds it. |
| Docker images | Postgres 17, Redis 8, Keycloak 26.x, Meilisearch 1.x, Mailpit 1.x | Majors are fixed here. At scaffold, look up the newest patch that is at least 7 days old and pin it by tag plus digest, never `:latest` (Locked rule). |

Rule: add a dependency only in the slice that first needs it, not all at scaffold. The scaffold adds exactly these: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` plus `flyway-database-postgresql`, `spring-boot-starter-actuator`, `org.postgresql:postgresql` (runtime), `spring-boot-docker-compose` (`optional`), `spring-modulith-starter-core`, `spring-modulith-starter-jdbc`, and `spring-modulith-starter-test` (test). Check each starter name against the Boot 4.1 docs when you pin it.

- Redis (`spring-boot-starter-data-redis`, Spring Session), validation and springdoc come in feature 4 or 5, whichever uses them first.
- Lombok comes with the first JPA entity (feature 3 or 5), under the guardrails below.
- The Redis container still starts at scaffold, because the done line needs it running, not connected.

### Settled scaffold details (my calls)

- **Event registry, JDBC flavour.** Use `spring-modulith-starter-jdbc` over `-jpa`. It is simpler, and Flyway owns its table in the `modulith` schema (`spring.modulith.events.jdbc.schema-initialization.enabled=false`, and the schema set to `modulith`). Copy the table DDL from the Modulith version you pinned. Runner up: the JPA registry. It also works, but it adds a JPA entity scan for a framework table.
- **Flyway wiring.** Use one Flyway run.
  - `spring.flyway.schemas` lists every module schema plus `modulith`, and `spring.flyway.locations` lists every `classpath:db/migration/<module>` folder.
  - Set `spring.flyway.default-schema=public`, so the table that records which migrations ran (`flyway_schema_history`) lives in `public`. Without this it lands in the first listed schema.
  - Version numbers are global, one block per module: `V<block>_<n>__<module>_<what>.sql`. Blocks: `modulith` 0, `identity` 1, `customer` 2, `catalog` 3, `search` 4, `inventory` 5, `cart` 6, `ordering` 7, `payment` 8, `notification` 9, `settings` 10, `promotion` 11, `review` 12. Example: `V3_1__catalog_create_schema.sql`.
  - At scaffold, each module folder gets one first migration that only creates its schema. Every folder then exists, and Flyway does not fail on an empty location.
  - `modulith` gets `V0_1__modulith_event_publication.sql`, with the event registry DDL copied from the pinned Modulith version.
  - Set `spring.jpa.hibernate.ddl-auto=validate` (Hibernate checks the tables, never changes them). Each entity names its own schema.
  - Runner up: one Flyway run per module, each with its own record table. That is cleaner for moving a module out later, but it is more setup than phase 1 needs. Feature 3 may change this; if it does, it records the change.
- **Lombok guardrails (entities only).**
  - Allowed on `@Entity` classes: `@Getter`, `@Setter` (only on fields that are allowed to change), and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
  - Never on entities: `@Data`, `@EqualsAndHashCode`, `@ToString`, `@Value`, `@Builder` (they break JPA proxies and lazy loading, or leak data into logs).
  - Never on records, DTOs, events or services.
  - Add a root `lombok.config` with `config.stopBubbling = true` and `lombok.addLombokGeneratedAnnotation = true`.
  - Declare Lombok in the compiler plugin's `annotationProcessorPaths`. Newer JDKs no longer find annotation processors automatically.
- **Boundary test.** `ModularityTests` in `src/test/java/com/kmicro/` calls `ApplicationModules.of(KmicroApplication.class).verify()` and writes docs with `Documenter` (output goes to `target/`, not committed). It is a plain JUnit test: no Spring context, no Docker, so it runs anywhere. This is the one standing test.
- **Package layout.** As locked in the design doc: `shared/` plus one package per module, each with `api/` and `events/` (marked `@NamedInterface`), `web/`, `domain/`, `persistence/`, and `package-info.java` holding `@ApplicationModule`. The scaffold creates every module package with `package-info.java` plus one small public class in `api/`, so Modulith sees each one as a module. Check that `verify()` lists all 12 modules.
- **Module wiring at scaffold.**
  - Mark `shared` as an open module (`@ApplicationModule(type = Type.OPEN)`), so every module may use it.
  - Write each module's `allowedDependencies` from the design doc diagram now. For example, `ordering` may use `cart::api`, `catalog::api`, `inventory::api`, `settings::api`, `payment::api` and `payment::events`, and `search` may use `catalog::events` and `inventory::events`.
  - Any module may also use `settings::api`, as the design doc example shows.
  - Then the boundary test checks real rules from day 1. A later feature that needs a new arrow adds it on purpose.
- **Spring profile and Compose.**
  - IntelliJ runs `KmicroApplication` with the Spring profile `local`. This is separate from the Compose profile `core`. Local only settings go in `application-local.yml`.
  - Use one `compose.yaml` at the repo root. Set `spring.docker.compose.profiles.active=core` and `spring.docker.compose.lifecycle-management=start-only`, so containers stay up between runs (Keycloak is slow to start).
  - Spring Boot connects to Postgres and Redis on its own. Keycloak, Meilisearch and Mailpit use fixed host ports with plain properties: Postgres 5432, Redis 6379, Keycloak 8081 (the app keeps 8080), Meilisearch 7700, Mailpit SMTP 1025 and web UI 8025.
  - Compose reads `.env`. The app's yml reads only what it needs through `${VAR:default}`.
  - The `messaging`, `aws` and `observability` profiles are added in the slices that use them.
- **Keycloak at scaffold.**
  - Keycloak uses the same Postgres container. A Postgres init SQL script creates a separate `keycloak` database and user.
  - Keycloak runs `start-dev --import-realm` with `KC_DB=postgres`.
  - The scaffold commits a minimal `kmicro` realm JSON, so the import works. Feature 4 fills in clients, roles and test users.
- **Other scaffold basics.**
  - Actuator exposes only `health`.
  - Spring Security is not added yet; it comes in feature 4.
  - Set `maven.compiler.release=25` and add `-parameters`.
  - The scaffold adds `.gitignore` (covering `.env` and `target/`) and `.env.example`. `.editorconfig` and the format and lint tools belong to feature 2.

### Configuration required (scaffold)

Values come from `.env` (git ignored). Commit `.env.example` with these names and safe placeholders:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: app database
- `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD`: Keycloak console and its own database
- `MEILI_MASTER_KEY`: Meilisearch key
- `REDIS_PASSWORD`: Redis auth (optional locally, required later)

Later slices add their own (Razorpay keys, the Keycloak client secret, SMTP for production).

## Consequences

**Positive**:
- One app to run, debug and deploy, with module boundaries that the build checks. That gives most of the microservice benefits without the cost.
- Every version is pinned and recorded. The "Versions" open item in the design doc is now closed.
- Virtual threads and Java 25 give good throughput for 20 to 40 testers with no tuning.

**Negative / tradeoffs**:
- Five containers in the `core` profile is a heavy laptop setup for a 2 week plan. The first Run is slow, and a broken container blocks startup.
- Lombok adds an annotation processor you must keep pinned and update when you upgrade Java. Entities follow different rules from the rest of the code.
- Spring Boot 4.2 lands soon after scaffold, so a minor upgrade comes early.
- Boot 4 brings Jackson 3 and modular starters. Older blog posts and examples (Boot 3) often do not apply as written.
- With one Flyway run, moving a module out later means splitting the record of which migrations ran.

**Neutral**:
- Hand written mapping means one small method per DTO. That is more typing, but nothing is hidden.
- Kafka, S3 and observability stay out until their slices, so the scaffold is smaller than the full design.

## Follow-up

- [ ] **Admin editable email templates (new feature you asked for).** Admins write templates in the UI. They are stored in the database with placeholders, and the notification module uses them. This is not in the design doc or the phase 1 scope yet. Enroll it with `/scope`, then design it with `/architect`. Security note for that spec: never let admins write raw Thymeleaf. Its expressions can run code on the server (server side template injection). Use plain `{{placeholder}}` substitution or a logic free engine for admin templates, and keep Thymeleaf for the built in layouts.
- [ ] Update the design doc (live artifact and copy) so the "Versions" open item says Java 25, Boot 4.1.2, Modulith 2.1.2. Note the extra choices too: Lombok on entities, yml config, virtual threads, meilisearch-java, Thymeleaf.
- [ ] At scaffold, confirm in the Spring Modulith reference docs that 2.1.x is the line for Boot 4.1.x before pinning.
- [ ] When the first event is published (feature 5), confirm that the Modulith 2.1 JDBC registry stores event records correctly with Jackson 3 (which Boot 4 uses).
- [ ] Plan the Spring Boot 4.2 and Modulith 2.2 upgrade once both are stable and at least 7 days old.
- [ ] Agent skills to review before any install (community, contents not checked): `sivaprasadreddy/sivalabs-agent-skills` (spring-boot and the Spring Modulith verifier). Install only with your approval, and not through `npx skills add` (AGENTS.md rule).
- [ ] MCP servers you may connect yourself later, in your own MCP settings: `grafana/mcp-grafana` (official, from slice 6), `redis/mcp-redis` (official, check its releases for a stable version), Postgres MCP Pro by Crystal DBA (community, run read only).
- [ ] For `/sync` and `/audit`: root `AGENTS.md` should record the Lombok guardrails, Java 25, and the chosen Agent Skills and MCP servers above.

# 0001. Rationale: Spring Modulith stack and module layout

The decision record behind [index.md](index.md). `/develop` does not need to read this file.

## Context

> ⚠️ Premise note: For 20 to 40 testers, Redis, Meilisearch, Keycloak and later Kafka from day 1 is more infrastructure than the load needs. A plain Postgres app could serve it. The owner locked these choices on purpose (production grade practice, and a path to microservices), so this spec does not reopen them. The real risk is the 2 week tester date, not the scale. So only the `core` profile is set up at scaffold, and each other piece arrives in the slice that needs it.

KMicro replaces the earlier kmicro microservices, which cost too much to build and run for one developer. The new backend must serve a web shop now and a mobile app later from one API, be safe (no forged headers, no access to other users' data, no prices set by the client), and allow any module to move out to its own service later without rewriting its logic (basis: `docs/design/backend-design.md`, Goals).

The design doc already locks the architecture, the modules, the infrastructure and the module rules. It left one item open: which Java, Spring Boot and Spring Modulith versions to use. It also left out a few scaffold choices that every later feature depends on: Maven coordinates, config format, threading model, boilerplate tools, how to map DTOs, how to talk to Meilisearch, the email template engine, and which Compose services start at scaffold.

The team is one developer. The tester launch is on 13 Nov 2026. Project rules: pin exact versions, prefer releases at least 7 days old, add few libraries, keep no secrets in the repo, and write only the boundary test up front (basis: `AGENTS.md`, Rules).

## Options considered

Full stacks, as weighed when the design doc was written, plus the sub choices made in this session.

### Option 1: Spring Modulith on one Spring Boot app (chosen)

One deployable app. Each module owns its package, its schema and its API, and talks to other modules through public `api` interfaces or events stored in the database. Spring Modulith checks the boundaries in a test.

**Pros**:
- One thing to run, debug and deploy, which suits a single developer (basis: monolith first).
- Boundaries are checked by a test, not by discipline. Events are stored, so none are lost in a crash.
- A clear path to move modules out (Kafka export, HTTP client behind the same `api` interface, one schema per module).

**Cons**:
- The rules are strict: you need snapshots instead of joins, and IDs instead of JPA relations. This adds code.
- It needs Spring Modulith know how, and there is less community material than for plain Spring Boot.

### Option 2: Microservices (the old kmicro shape)

One service per domain, an API gateway, Kafka between services.

**Pros**:
- Each service can scale and release on its own.
- Every boundary is a hard process boundary.

**Cons**:
- Roughly 3 times the build and run effort for a team of one. The old kmicro showed this (basis: premature microservices failure pattern).
- Distributed failures, network hops and a separate deploy for every change.

### Option 3: Plain layered Spring Boot monolith

Controllers, services and repositories, organised by layer, with no module rules.

**Pros**:
- Fastest to start, most familiar, and the most examples online.

**Cons**:
- Nothing stops modules from reaching into each other's tables. Moving a module out later turns into a rewrite.
- No stored module events, so the outbox has to be written by hand again (the old kmicro did this).

### Sub choices made in this session

| Choice | Picked | Runner up | Why |
|---|---|---|---|
| Java | 25 LTS | 21 LTS | Longer support. Virtual thread pinning fixed. Boot 4 supports it. |
| Spring Boot line | 4.1.x (4.1.2) | Wait for 4.2 GA | 4.2 is a release candidate. Waiting breaks the "stable, 7 days old" rule and costs days. |
| Lombok | Entities only, with guardrails | No Lombok | Your call (see Rationale). |
| DTO mapping | Hand written | MapStruct | No processor, no magic. Fits "few libraries". |
| Config | `application.yml` | `.properties` | Many nested integrations. |
| Virtual threads | On | Normal Tomcat pool | Calls are mostly blocking I/O. Java 25 fixes pinning. |
| Coordinates | `com.kmicro:kmicro-backend` | `com.kmicro:kmicro` | Avoids a clash with the old repo name. |
| Compose at scaffold | `core` profile only | All four profiles | Smaller first run. Other profiles arrive with their slices. |
| Email engine | Thymeleaf (for now) | FreeMarker, or decide in feature 5 | Spring's usual choice for HTML mail, easy reuse of the kmicro templates. |
| Meilisearch client | Official `meilisearch-java` | Hand written `RestClient` | Typed API for index settings, filters and sort. |
| Event registry | JDBC flavour | JPA flavour | Simpler. Flyway owns the table. |
| Flyway | One run, a version block per module | One run per module | Less setup now. Can be split when a module moves out. |

## Rationale

The architecture is the owner's locked choice. It fits the forces above: a single developer and a short deadline call for one deployable app (basis: monolith first), and the goal of moving modules out later calls for enforced boundaries plus stored events. Option 3 is faster on day 1 but fails the day 730 goal. Option 2 serves the day 730 goal but fails day 1 and day 180 for a team of one.

On versions, starting on the newest stable line (Boot 4.1.2, Modulith 2.1.2) on Java 25 gives the longest runway before a forced upgrade. It still respects the 7 day rule, because 4.1.2 came out on 22 Oct. Starting on 4.2 RC would break the pinning rule. Starting on Boot 3.x would mean a big framework upgrade right after launch.

On Lombok: I recommended no Lombok. You picked Lombok for entities only. That works, and it does cut real noise on JPA entities. It costs an annotation processor you have to keep in step with new JDKs, and its common annotations (`@Data`, `@EqualsAndHashCode`, `@ToString`) are known to break JPA entities (proxy equality, lazy loading, data in logs). The guardrails in `index.md` keep the useful part and block the harmful part, so I accept the choice with those guardrails.

The other sub choices all follow "few libraries, boring tools": hand written mapping, the official Meilisearch client, Thymeleaf (Spring's usual HTML mail engine), yml config, and virtual threads (built into the JDK, switched on with one property).

## References

**Project sources**:
- `docs/design/backend-design.md`: Decisions table, Architecture, Module rules, Data and money, Security, API design, Infra and local dev, Testing, Reuse from kmicro, Open items
- `AGENTS.md`: Rules (pinning, 7 day releases, few tools, no secrets, testing approach)
- `docs/scope/scope.md`: feature 1, build approach (Tracer Bullet), tester date

**Practices & standards**:
- Monolith first; premature microservices failure pattern
- Boring technology; few dependencies with exact versions and a 7 day cooldown
- Transactional outbox (here, the Spring Modulith event publication registry)
- RFC 9457 Problem Details
- JPA entity equality and Lombok pitfalls (no `@Data` on entities)
- Server side template injection (for the future admin email template feature)

**Links** (web verified in this session's version check, 2026-10-30):
- Spring Boot v4.1.2 release: https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2
- Spring Boot versions (Maven Central): https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml
- Spring Modulith versions (Maven Central): https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-core/maven-metadata.xml
- springdoc webmvc ui versions (Maven Central): https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml
- ShedLock versions (Maven Central): https://repo1.maven.org/maven2/net/javacrumbs/shedlock/shedlock-core/maven-metadata.xml

Lombok 1.18.48 and Bucket4j 8.20.0 came only from search results (their pages were not opened), so they are cited by name, with no link.

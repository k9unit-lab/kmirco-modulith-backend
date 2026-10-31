# Verify: Stack & architecture · spec 0001 · updated 2026-10-31
_Steps derived from spec 0001 requirements (its "done when" lines). `/check verify` runs these; `/test` locks the durable ones._

AC map (the spec lists its contract under `## Requirements`, not as numbered ACs):
- **AC-1**: the empty app starts from IntelliJ with Postgres, Redis, Keycloak, Meilisearch and Mailpit started for you (`core` Compose profile).
- **AC-2**: the Spring Modulith boundary test passes.

Before you start: Docker is running, and `.env` exists (copy `.env.example`, change the passwords, keep `MEILI_MASTER_KEY` at least 16 characters).

## UI / manual
- [ ] In IntelliJ, run the `KmicroApplication` run configuration (Spring profile `local`) → the app starts with no errors, and the log shows Docker Compose starting the `core` services → AC-1
- [x] Watch the startup log → Flyway applies the 13 first migrations (`V0_1` modulith through `V12_1` review), and the app logs `Started KmicroApplication` → AC-1
- [x] Open http://localhost:8080/actuator/health → `{"status":"UP"}` → AC-1
- [x] Open http://localhost:8081 and log in with `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` → the admin console loads, and the realm list shows `kmicro` → AC-1
- [x] Open http://localhost:8025 → the Mailpit inbox loads → AC-1
- [x] Open http://localhost:7700 → the Meilisearch mini dashboard loads and accepts `MEILI_MASTER_KEY` → AC-1
- [x] Stop the app, then run it again → the containers keep running (`start-only`), and the second start is faster → AC-1

## Commands
- [x] `docker compose --profile core ps` → 5 services (`postgres`, `redis`, `keycloak`, `meilisearch`, `mailpit`) all `running`, and postgres `healthy` → AC-1
- [x] `docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\dn'` → schemas `modulith`, `identity`, `customer`, `catalog`, `search`, `inventory`, `cart`, `ordering`, `payment`, `notification`, `settings`, `promotion`, `review` (plus `public`) → AC-1
- [x] `docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'select version, success from public.flyway_schema_history order by installed_rank'` → 13 rows, all `success = t`, and the table is in `public` → AC-1
- [x] `docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\dt modulith.*'` → the event publication table exists in `modulith` → AC-1
- [x] `docker compose exec postgres psql -U postgres -l` (or with `$POSTGRES_USER`) → a separate `keycloak` database exists → AC-1
- [x] `curl -s http://localhost:8081/realms/kmicro` → JSON with `"realm":"kmicro"` (the realm import worked) → AC-1
- [x] `curl -s http://localhost:7700/health` → `{"status":"available"}` → AC-1
- [x] `docker compose exec redis redis-cli ping` (add `-a "$REDIS_PASSWORD"` if you set one) → `PONG` → AC-1
- [x] `./mvnw test -Dtest=ModularityTests` → both tests pass, with no Docker needed → AC-2
- [x] After that test, open `target/spring-modulith-docs/` → one module page each for `identity`, `customer`, `catalog`, `search`, `inventory`, `cart`, `ordering`, `payment`, `notification`, `settings`, `promotion`, `review` (12 modules), plus the open `shared` module → AC-2
- [x] Negative check: in any module, import a class from another module's internal package (for example `catalog.domain` from `cart`), then run `./mvnw test -Dtest=ModularityTests` → `verifiesModuleBoundaries` fails and names the illegal dependency. Revert the change afterwards → AC-2

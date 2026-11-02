# Verify: Data model · spec 0002 · updated 2026-11-02
_Steps derived from spec 0002 acceptance criteria. `/check verify` runs these; `/test` locks the durable ones._

## Commands
- [x] `./mvnw verify` → passes (compile with warnings as errors, format check, module boundary test) → AC-6
- [x] In jshell with `target/classes`: `Money.of("1.005", INR).rounded()` → `1.01 INR` → AC-1
- [x] `Money.of("1.5", JPY).rounded()` → `2 JPY`; `Money.of("1.234", USD).rounded()` → `1.23 USD` (decimals come from `CurrencyCode.decimals()`) → AC-1
- [x] `Money.of("10", INR).plus(Money.of("1", USD))` → throws `CurrencyMismatchException`; `CurrencyCode.of("ABC")` → throws `IllegalArgumentException` → AC-1
- [x] Make 100,000 ids with `UuidV7.next()` → every one has `version() == 7`, all unique, and the list is already sorted → AC-2
- [x] `SellerId.HOUSE.value()` → `00000000-0000-7000-8000-000000000001`, same on every run; `ProductId.of(null)` → `NullPointerException` → AC-2, AC-3

## Real app
- [x] Wipe the local Postgres volume (`docker compose --profile core down -v`), start the app from IntelliJ → Flyway applies 15 migrations, app starts with `ddl-auto: validate`, `/actuator/health` is `UP` → AC-5
- [x] `psql`: `select * from pg_sequences where schemaname = 'ordering'` → `order_number_seq`, start 1, no cycle → AC-5
- [x] `psql`: `\d public.shedlock` → `name` PK, `lock_until`, `locked_at`, `locked_by` → AC-5
- [x] Throwaway entity on `VersionedUuidEntity` with SQL logging: a new save runs one `INSERT` and no select first; saving the loaded entity runs one `UPDATE` (no second insert), version goes 0 → 1, id is version 7, `created_at` is filled in UTC from the shared `Clock`, `created_by` is null → AC-4
- [x] Change the machine time zone (for example `TZ=Asia/Kolkata`) and repeat the save → `created_at` in the database is still the right UTC instant (`hibernate.jdbc.time_zone: UTC`) → AC-4

## Owner review
- [ ] Read the *Later phase readiness* and *Slice handoff* tables in the spec with the owner; each later phase needs only additive changes → AC-7

## Acceptance-criteria coverage
- AC-1 covered by the three `Money`/`CurrencyCode` steps · AC-2 by the `UuidV7` and typed ID steps · AC-3 by the `SellerId.HOUSE` step · AC-4 by the throwaway entity and time zone steps · AC-5 by the wipe and start, sequence and shedlock steps · AC-6 by `./mvnw verify` · AC-7 by the owner review

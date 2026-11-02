# 0002. Data model: reasoning

## Context

KMicro is a Spring Modulith with 12 phase 1 modules, each owning one Postgres schema. The design doc already locks the big rules: one schema per module, no cross schema joins or foreign keys, UUIDv7 IDs, Money as `numeric(19,4)` + `char(3)`, an order number from a database sequence, the order status machine, and snapshots on orders. What it does not settle is the shape of each table, the conventions every table shares, and how much to build before the first slice.

The forces: one developer, a tester launch on 13 Nov 2026, and a Tracer Bullet build approach that wants a thin real path first. At the same time the scope requires that sellers, partial refunds and abandoned carts arrive later without a breaking migration. Getting a shared convention wrong now (how money, IDs or audit columns are stored) would be copied into every module and be costly to undo.

Postgres is version 17, which has no built in `uuidv7()`. Spring Security is not yet on the classpath (feature 4), so the current user is unknown for now.

## Options considered

### Option 1: Target model in the spec, shared base now, tables per slice

Write the whole model here; build Money, typed IDs, the UUID helper, the base entity and the order number sequence now; each slice migrates its own tables.

**Pros**:
- No unused tables; each slice's migration is small and reviewed with the code that uses it.
- The shared rules exist before the first table, so every slice copies the same pattern.

**Cons**:
- The database is only complete at the end of phase 1.
- A slice can drift from the spec if no one checks; the handoff table is the guard.

### Option 2: Every phase 1 table now

One migration set creating all tables before feature 5.

**Pros**:
- The done line ("Flyway creates every phase 1 schema") is met literally on day one.

**Cons**:
- Tables are designed before the code that uses them; slices will find mistakes and need fix up migrations.
- Large up front work right before a two week launch target.

### Option 3: Spec only, no code

**Pros**:
- Least work now.

**Cons**:
- Slice 1 has to build the shared base under time pressure, mixed with the buying loop.

### Sub decision: how modules refer to a sellable item

SKU text (design doc wording) versus `variant_id`. SKU is readable and was the doc's plan, but the admin can edit it, so stored SKUs in cart, stock and wishlist would go stale. `variant_id` never changes. The owner chose `variant_id`; a lock on the SKU after first use was the other option.

## Rationale

Option 1 fits Tracer Bullet: the first real thread (feature 5) needs only the shared base plus its own tables, and every later slice gets a ready pattern. The full target model still exists on paper, which is what makes the "later phases are additive" check possible without building every table now (basis: Tracer Bullet build approach, `AGENTS.md`; basis: evolutionary database design).

Owner choices that differ from my recommendation, recorded on purpose:
- **Own `UuidV7` helper** instead of Hibernate's `@UuidGenerator(style = VERSION_7)`. Works fine; it is a little more code to own and test.
- **`created_by` / `updated_by` on every table.** More audit value, but these stay empty until feature 4 and add columns to every table.
- **`version` only on products, variants, stock and settings.** Matches the design doc list; other admin tables can lose an update when two admins edit at once.
- **Raw guest cart token.** Simpler to debug; a database leak lets someone take over guest carts. Carts hold no personal data, so the risk is low.
- **Archived (not deleted) reviews.** Keeps history, but holds content the customer asked to remove; slice 10 must clear it on account deletion.

The other calls follow standard practice: `varchar` + `CHECK` for status values because they are easy to extend (basis: Postgres enum types are hard to alter); one currency per row because the store has one currency and mixing must be impossible (basis: locked Money rule, design doc); typed columns for snapshots because they can be validated and anonymized (basis: snapshot rule, design doc module rules); a separate refunds table and a `seller_id` that is never null so later phases only add rows (basis: additive, expand only migrations); usage rows with a lock on the coupon row instead of counters so the counts can't drift (basis: pessimistic locking for limited stock); idempotency keys in the same transaction as the order (basis: idempotency keys for money operations).

In Java, entities use plain `UUID` ids and fields; typed IDs are used in `api` and `events` DTOs. JPA does not apply converters to `@Id` fields, and custom Hibernate types per ID add code for little gain (runner up: a Hibernate `@JavaType` per ID). Entities implement `Persistable` because their id is set before save; without it Spring Data runs a select and a merge for every new row.

## References

**Project sources**:
- `docs/design/backend-design.md`: Decisions table, Modules, Module rules, Data and money, Order status, API conventions
- `docs/scope/scope.md`: feature 3 done line, build approach, later phases
- `AGENTS.md`: Flyway naming, version blocks, rules
- Spec 0001 (stack and module layout)

**Practices & standards**:
- Tracer Bullet delivery
- Evolutionary database design, expand only migrations
- Idempotency keys for money operations
- Pessimistic row locking for limited resources
- Snapshot pattern for orders
- ISO 4217 currency codes, ISO 3166 country codes
- RFC 9562 UUID version 7

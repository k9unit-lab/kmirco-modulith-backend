# 0002. Phase 1 data model and shared persistence base

**Date**: 2026-11-02
**Status**: Proposed

## Summary

This spec holds the full phase 1 data model: every table, in every module schema, that the shop will need. It is the target, not one big migration. This feature builds only the shared base now (Money, typed IDs, a UUIDv7 helper, a base entity with audit columns, and the order number sequence). Each later slice creates its own module's tables from this model when it is built. The model is shaped so sellers, partial refunds and abandoned carts can come later by only adding things, never by breaking or moving data.

## Requirements

**User stories**:
- As the developer building each slice, I want one agreed target model so that every module's tables follow the same rules and fit together.
- As the owner, I want later phases (marketplace sellers, partial refunds, abandoned cart reminders) to need only additive migrations so that live data never has to be moved or rewritten.

**Acceptance criteria**:
- **AC-1**: `shared` has a `CurrencyCode` (ISO 4217, checked with `java.util.Currency`) and a `Money` value (`BigDecimal amount` + `CurrencyCode currency`). `Money` rounds to the currency's own decimals (2 for INR and USD, 0 for JPY), rounding half up. Adding or subtracting two different currencies throws.
- **AC-2**: `shared` has a `UuidV7` helper whose IDs are version 7 UUIDs and sort by creation time, plus typed ID records (`CustomerId`, `CategoryId`, `ProductId`, `VariantId`, `OrderId`, `SellerId`, and others as slices need them) wrapping a `UUID`.
- **AC-3**: `SellerId.HOUSE` is a fixed constant UUID in `shared`, the seller of every product and order line in phase 1.
- **AC-4**: `shared` has a persistence base that fits every key shape: `AuditedEntity<ID>` (audit columns filled automatically, `Persistable` so a new entity is inserted with no select first, no id of its own), `UuidEntity` on top of it (UUIDv7 id set in Java at creation), and an optional `version` column usable on either (so `stock_levels` keyed by `variant_id` and `store_settings` keyed by `id = 1` can be versioned).
- **AC-5**: A Flyway migration creates `ordering.order_number_seq`, and one creates the `public.shedlock` table for scheduled jobs. On an empty database, Flyway runs all migrations cleanly and the app starts with `ddl-auto: validate`.
- **AC-6**: `./mvnw verify` passes (compile with warnings as errors, format check, module boundary test).
- **AC-7**: The model below shows that each later phase (sellers, partial refunds, abandoned cart) needs only additive changes: a new table, new rows, a new nullable column, or a dropped constraint. Checked by reading the *Later phase readiness* table.

## Decision

**Chosen option**: Option 1: target model in the spec, shared base now, tables per slice.

Record the whole phase 1 model here, build the shared persistence base in this feature, and let each slice migrate its own module's tables (see *Slice handoff*). Other modules point to a variant by its `variant_id` (UUID), never by SKU text.

## Rationale

Reasoning and options: see [rationale.md](rationale.md).

## Feature design

### Rules every table follows

| Rule | What it means |
|---|---|
| Names | `snake_case`, plural table names (`catalog.products`, `ordering.order_lines`). The schema is the module, so no prefix in the table name. |
| Primary key | `id uuid` UUIDv7, set in Java by `UuidV7.next()` when the entity is created (Postgres 17 has no `uuidv7()`). Exceptions use a natural or composite key, as listed per table (`users`, `customer_profiles`, `stock_levels`, `store_settings`, `product_rating_summaries`, join tables, `cart_items`, `wishlist_items`, `idempotency_keys`, `review_eligibilities`). |
| Events | Payloads carry everything a listener needs (notification only listens, so it never calls back). State events (`ProductChanged`, `PriceChanged`, `StockLevelChanged`, `ProductRatingChanged`) carry the full current state plus a source version (`products.version`, `stock_levels.version`, summary `updated_at`), so a listener can ignore an older event that arrives late. |
| Links | A foreign key only inside one schema. Across modules: a plain `uuid` column with no FK (locked rule). |
| Variant reference | Every module that means "one sellable item" stores `variant_id uuid`. The SKU is an editable, readable label only. Order lines also keep the SKU text as a snapshot. |
| Money | `numeric(19,4)` for each amount, and **one** `currency char(3)` per row that all its amounts share. |
| Status and type values | `varchar(30)` + a `CHECK (col IN (...))` constraint, mapped with `@Enumerated(EnumType.STRING)`. |
| Time | `timestamptz`, stored and handled in UTC (`Instant` in Java). `created_at` and `updated_at` get `DEFAULT now()` in SQL so seed and plain SQL inserts work. |
| Partial and `lower()` indexes | Written only in Flyway SQL. Never declare them with `@Table(uniqueConstraints)` or `@Index` (that would create a plain, non partial index). |
| Audit columns | Every table: `created_at`, `updated_at` (not null), `created_by`, `updated_by` (`uuid`, null means system, guest, or no login). Append only tables (history, logs, movements) have only `created_at` and `created_by`. |
| Version | `version bigint not null` only on `catalog.products`, `catalog.variants`, `inventory.stock_levels`, `settings.store_settings` (the admin edit tables from the design doc). |
| Deleting | Admin data gets `archived_at timestamptz` (null = live) and is never deleted. Unique rules apply only to live rows (partial unique index `WHERE archived_at IS NULL`). Real deletion only for personal data on account deletion (slice 10). |
| Snapshots | Plain typed columns on the order, never JSON. |
| Seller | `seller_id uuid not null` on `catalog.products` and `ordering.order_lines`, always `SellerId.HOUSE` in phase 1. |

### Data model sketch

Columns listed are beyond the standard ones (`id`, audit columns, `version`, `archived_at` as the rules above say). **PK** = primary key, **FK** = foreign key (same schema only), **UQ** = unique.

**identity**
- `users`: `id` PK = Keycloak `sub` (not generated), `email varchar(320) not null`, `status` (`ACTIVE`, `DISABLED`, `DELETED`), `first_login_at timestamptz null`, `guest_orders_linked_at timestamptz null` (last verified login that ran linking). Roles are not stored; they come from the token.

**customer**
- `customer_profiles`: `customer_id uuid` PK, `full_name null`, `phone varchar(20) null`.
- `addresses`: `customer_id uuid not null`, `full_name`, `phone`, `line1`, `line2 null`, `city`, `state`, `postal_code`, `country char(2)` (ISO 3166), `is_default boolean`, `archived_at`. UQ one default per customer: `(customer_id) WHERE is_default AND archived_at IS NULL`.
- `wishlist_items`: PK `(customer_id, variant_id)`, `created_at`.
- `consents`: `customer_id`, `type` (`MARKETING_EMAIL`, `PUSH`), `policy_version varchar(20)`, `granted_at`, `withdrawn_at null`. Append only style: a change of mind adds a row.

**catalog**
- `categories`: `parent_id null` FK `categories`, `name`, `slug` UQ (live rows), `sort_order int`, `archived_at`. A tree; one category per product.
- `products`: `category_id` FK `categories` not null, `seller_id not null`, `name`, `slug` UQ (live rows), `description text null`, `active boolean`, `archived_at`, `version`.
- `option_types`: `name` UQ on `lower(name)` (live rows), `archived_at`.
- `option_values`: `option_type_id` FK, `value`, `sort_order`, `archived_at`. UQ `(option_type_id, lower(value))` (live rows).
- `product_option_types`: PK `(product_id, option_type_id)`, both FK, `position int`.
- `variants`: `product_id` FK not null, `sku varchar(64)` UQ (live rows, editable), `price_amount numeric(19,4) not null`, `price_currency char(3) not null`, `weight_grams int null`, `active boolean`, `archived_at`, `version`. A product with no options has exactly one variant.
- `variants` also has `option_key varchar(500) not null`: the sorted option value ids joined as text (empty for a product with no options). UQ `(product_id, option_key) WHERE archived_at IS NULL`, so no two live variants have the same option mix.
- `variant_option_values`: PK `(variant_id, option_value_id)`, both FK, plus `option_type_id` with UQ `(variant_id, option_type_id)` (one value per option type per variant).
- `product_images`: `product_id` FK not null, `variant_id` FK null (null = image for the whole product), `storage_key varchar(500)` (the file's path inside `FileStorage`, not a URL), `alt_text null`, `sort_order`.

**search**
- `index_rebuilds`: `status` (`RUNNING`, `DONE`, `FAILED`), `started_at`, `finished_at null`, `documents_sent int`, `error text null`, `heartbeat_at timestamptz not null` (updated every batch). UQ one running rebuild: `((true)) WHERE status = 'RUNNING'`. A new rebuild may mark a `RUNNING` row `FAILED` when its `heartbeat_at` is older than 5 minutes (crash recovery).

**inventory**
- `stock_levels`: `variant_id` PK, `on_hand int`, `reserved int`, `version`. CHECK `0 <= reserved AND reserved <= on_hand`. Available = `on_hand - reserved`.
- `stock_reservations`: `order_id` UQ, `status` (`ACTIVE`, `CONFIRMED`, `RELEASED`, `EXPIRED`, `RETURNED`). No expiry clock of its own: it moves only on ordering's events (`OrderPaid`, `OrderExpired`, `OrderCancelled`).
- `stock_reservation_lines`: PK `(reservation_id, variant_id)`, `reservation_id` FK, `quantity int > 0`.
- `stock_movements` (append only): `variant_id`, `type` (`INITIAL`, `ADJUSTMENT`, `SALE`, `RETURN`), `quantity_change int`, `on_hand_after int`, `reason null`, `order_id null`. Logs changes to `on_hand` only; reserve and release change only `reserved` and are tracked by the reservation rows. UQ `(order_id, type) WHERE order_id IS NOT NULL` so a repeated event writes no second `SALE` or `RETURN`.
- Admin stock changes are deltas (`+5`, `-2`) with no `If-Match`, since checkout bumps `version` constantly. `If-Match` is used only for an absolute "set on hand to N". A change that would put `on_hand` below `reserved` is refused with 409 `STOCK_BELOW_RESERVED`.

**cart**
- `carts`: `customer_id null`, `guest_token varchar(64) null` UQ (stored raw), `status` (`ACTIVE`, `CONVERTED`, `MERGED`), `coupon_code null` (added in slice 8), `last_activity_at not null`. CHECK exactly one of `customer_id`, `guest_token` is set while `ACTIVE`; when a guest cart becomes `MERGED` or `CONVERTED` its `guest_token` is set to null, so an old token can never reopen it (the CHECK is `status <> 'ACTIVE' OR ((customer_id IS NULL) <> (guest_token IS NULL))`). UQ one active cart per customer: `(customer_id) WHERE status = 'ACTIVE'`. Index `(status, last_activity_at)` for abandoned cart and cleanup.
- `cart_items`: PK `(cart_id, variant_id)`, `cart_id` FK, `quantity int > 0`. No price (locked).

**ordering**
- `orders`: `order_number varchar(40)` UQ, `customer_id null` (null = guest order), `contact_email varchar(320) not null` (email snapshot for every order, guest or customer), `cart_id uuid not null` (the cart it came from), `create_account_requested boolean not null default false`, `status` (the locked status list), `currency`, `items_total`, `discount_total`, `tax_total`, `shipping_total`, `grand_total`, tax snapshot `tax_name`, `tax_rate numeric(5,2)`, `prices_include_tax boolean`, ship address snapshot `ship_full_name`, `ship_phone`, `ship_line1`, `ship_line2 null`, `ship_city`, `ship_state`, `ship_postal_code`, `ship_country char(2)`, `coupon_code null`, `expires_at`, `paid_at null`, `cancelled_at null`, `cancel_reason null`. CHECK `status <> 'REFUNDED' OR paid_at IS NOT NULL`. Index `(customer_id, created_at)`, `(status, expires_at)`, `lower(contact_email) WHERE customer_id IS NULL` (guest order linking).
- `order_lines`: `order_id` FK, `variant_id`, `product_id`, `seller_id`, snapshot `sku`, `product_name`, `options_label` (e.g. `Size: M, Color: Red`), `currency`, `unit_price`, `quantity int > 0`, `line_subtotal`, `line_discount`, `line_tax`, `line_total`.
- `order_status_history` (append only): `order_id` FK, `from_status null`, `to_status`, `actor_type` (`CUSTOMER`, `GUEST`, `ADMIN`, `SYSTEM`), `actor_id null`, `note null`.
- `shipments`: `order_id` FK UQ, `carrier`, `tracking_number`, `tracking_url null`, `shipped_at`, `delivered_at null`.
- `order_refunds`: `order_id` FK, `currency`, `amount`, `method` (`MANUAL`; later `PROVIDER`), `reference`, `refunded_at`, `note null`. UQ `(order_id, reference)`.
- `idempotency_keys`: PK `(cart_id, idempotency_key varchar(100))`, `request_hash char(64)`, `order_id`, `created_at`. Deleted after 24 hours by a job.
- Sequence `order_number_seq` (start 1, no cycle, no max).

**payment**
- `payment_attempts`: `order_id`, `provider` (`FAKE`, `RAZORPAY`), `provider_order_id` UQ null, `idempotency_key varchar(100) not null`, `status` (`CREATED`, `SUCCEEDED`, `FAILED`, `EXPIRED`, `CAPTURED_UNAPPLIED`), `currency`, `amount`, `failure_code null`, `failure_reason null`. UQ `(order_id, idempotency_key)` (a double clicked retry makes one attempt). UQ one open attempt per order: `(order_id) WHERE status = 'CREATED'`. UQ one success per order: `(order_id) WHERE status = 'SUCCEEDED'`.
- `provider_payments`: `attempt_id` FK, `provider_payment_id` UQ, `status` (`CAPTURED`, `FAILED`), `amount`, `currency`, `captured_at null`. One Razorpay order can hold several payments (one fails, the next captures).
- `CAPTURED_UNAPPLIED` means money was captured but the order was no longer `PENDING_PAYMENT` (late webhook after expiry or cancel, or a second capture). It never fails the webhook; it raises a `PaymentNeedsRefund` event and shows on an admin list so the admin refunds it by hand.
- `webhook_events` (append only): `provider`, `provider_event_id`, `event_type`, `payload jsonb`, `received_at`, `processed_at null`. UQ `(provider, provider_event_id)`. Deleted after 90 days by a job.

**notification**
- `email_deliveries`: `source_key varchar(150)` UQ, built as `<template>:<eventId>` (one event can send two emails, e.g. order confirmation and set password), `template`, `recipient`, `subject`, `body text` (rendered once, so a retry resends the same content), `status` (`PENDING`, `SENT`, `FAILED`), `attempts int`, `next_attempt_at null`, `last_error null`, `sent_at null`.

**settings**
- `store_settings`: `id smallint` PK CHECK `id = 1` (single row), `store_name`, `currency char(3)`, `tax_name`, `tax_rate numeric(5,2)` CHECK 0 to 100, `prices_include_tax`, `flat_shipping_fee numeric(19,4)`, `free_shipping_above numeric(19,4) null`, `order_number_prefix varchar(20)`, `unpaid_order_timeout_minutes int`, `version`. Its migration inserts the one default row.

**promotion**
- `coupons`: `code` UQ on `lower(code)` (live rows), `discount_type` (`PERCENT`, `FIXED`), `percent_off numeric(5,2) null`, `max_discount null`, `fixed_amount null`, `currency char(3) null` (for the amount columns), `min_order_amount null`, `starts_at`, `ends_at null`, `total_limit int null`, `per_customer_limit int not null default 1`, `active`, `archived_at`. CHECK the right amount columns are set for the type, and CHECK `currency IS NOT NULL` whenever `max_discount`, `fixed_amount` or `min_order_amount` is set. A coupon whose `currency` is not the store currency is invalid at checkout.
- `coupon_usages`: `coupon_id` FK, `order_id` UQ, `customer_id null`, `email varchar(320) not null` (every usage, guest or customer), `status` (`RESERVED`, `REDEEMED`, `RELEASED`), `currency`, `discount_amount`. Index `(coupon_id, customer_id)` and `(coupon_id, lower(email))`. The per customer limit counts rows matching `customer_id` OR `lower(email)`, so checking out as a guest with the same email does not skip the limit.

**review**
- `reviews`: `product_id`, `customer_id`, `rating smallint` CHECK 1 to 5, `body varchar(2000) null`, `hidden boolean`, `hidden_reason null`, `archived_at`. UQ `(customer_id, product_id) WHERE archived_at IS NULL`. A customer "delete" sets `archived_at`.
- `review_eligibilities`: PK `(customer_id, product_id)`, `order_id`, `delivered_at`.
- `product_rating_summaries`: `product_id` PK, `review_count int`, `rating_sum int`. Average is computed on read (`rating_sum / review_count`).

**modulith**: `event_publication` already exists (V0_1). Completed publications are purged after 7 days by a job (they hold personal data from event payloads).

**public**: `shedlock` (name PK, `lock_until`, `locked_at`, `locked_by`), the standard ShedLock table for scheduled jobs (order expiry, cleanups). Created by this feature in `db/migration/modulith/V0_2__modulith_shedlock.sql`.

### Relationships (inside each schema)

| From | To | Cardinality |
|---|---|---|
| `categories` | `categories` (parent) | 1:N |
| `categories` | `products` | 1:N |
| `products` | `variants`, `product_images` | 1:N |
| `products` | `option_types` via `product_option_types` | N:M |
| `variants` | `option_values` via `variant_option_values` | N:M |
| `option_types` | `option_values` | 1:N |
| `stock_reservations` | `stock_reservation_lines` | 1:N |
| `carts` | `cart_items` | 1:N |
| `orders` | `order_lines`, `order_status_history`, `order_refunds` | 1:N |
| `orders` | `shipments` | 1:1 now (1:N later) |
| `coupons` | `coupon_usages` | 1:N |

Cross module (no FK, ID only): `variant_id` in inventory, cart, customer wishlist and ordering lines points to `catalog.variants`; `customer_id` everywhere is the Keycloak `sub`; `order_id` in inventory, payment and promotion points to `ordering.orders`; `product_id` in review points to `catalog.products`.

### Later phase readiness

| Later phase | What it needs | Change type |
|---|---|---|
| Multi vendor sellers | A `sellers` table in a new module; new rows use real seller ids. `seller_id` already on products and order lines. Also new: `shipments.seller_id` (nullable column), a seller sub order table for per seller fulfilment, and a payouts model. | Additive |
| Partial refunds via API | More `order_refunds` rows with `method = 'PROVIDER'`; new nullable `provider_refund_id` and a `status` column (`PENDING`, `DONE`, `FAILED`, default `DONE` for old rows); a new `order_refund_lines` table; `refunded_at` becomes nullable (relaxing a constraint). The refund cap then counts `PENDING` rows too. Per line amounts are already stored. | Additive |
| Abandoned cart reminders | Query `ACTIVE` customer carts by `last_activity_at` (indexed); consent already in `customer.consents`; a new reminder log table. Guest carts have no email, so they get no reminder unless a nullable `carts.email` is added later. | Additive |
| Multiple shipments (later, not required) | Drop the UQ on `shipments.order_id`. | Drop constraint |

### State transitions

- Order: the locked status machine in the design doc (`PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `EXPIRED`, `REFUNDED`). Every move writes one `order_status_history` row in the same transaction. Each move loads the order with `SELECT ... FOR UPDATE` first, so a webhook and the expiry job can't both win.
- A repeated event that would move the order to the status it already has (for example a second `PaymentFailed`) writes nothing, so duplicates leave no extra history rows.
- Stock reservation: `ACTIVE` → `CONFIRMED` (`OrderPaid`) · `RELEASED` (`OrderCancelled` before payment) · `EXPIRED` (`OrderExpired`). `CONFIRMED` → `RETURNED` (`OrderCancelled` with `wasPaid = true`): raises `on_hand` and writes a `RETURN` movement. An event for a reservation already in a final state is a no op.
- Cart: `ACTIVE` → `CONVERTED` (order paid) · `MERGED` (guest cart merged on login).
- Coupon usage: `RESERVED` → `REDEEMED` (paid) · `RELEASED` (expired, or cancelled before payment). A `REDEEMED` usage stays used even if the admin later cancels the paid order.
- Payment attempt: `CREATED` → `SUCCEEDED` · `FAILED` · `EXPIRED` (order expired or cancelled while open). `FAILED` → `SUCCEEDED` (a later capture on the same Razorpay order). `SUCCEEDED` is final: a late `payment.failed` never moves it back. Any capture whose order is no longer `PENDING_PAYMENT` → `CAPTURED_UNAPPLIED`.

### API surface

No new HTTP endpoints in this feature. The Java surface it adds in `shared`:

| Type | Members | Errors |
|---|---|---|
| `UuidV7` | `static UUID next()` | none |
| Typed IDs (records) | `of(UUID)`, `value()`, `static newId()`; `SellerId.HOUSE` | `NullPointerException` on null |
| `CurrencyCode` | `of(String)`, `code()`, `decimals()` | `IllegalArgumentException` on a non ISO code |
| `Money` | `of(BigDecimal, CurrencyCode)`, `of(String, CurrencyCode)`, `zero(CurrencyCode)`, `plus`, `minus`, `times(int)`, `rounded()`, `isNegative()`. No `double` factory. | `CurrencyMismatchException` when currencies differ |
| `AuditedEntity<ID>` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`) | audit fields, `Persistable<ID>`: a `@Transient boolean isNew = true` that `@PostLoad` and `@PostPersist` set to false; subclasses declare their own `@Id` (natural or `@EmbeddedId` composite) | none |
| `UuidEntity` (extends `AuditedEntity<UUID>`) | `@Id UUID id` set by `UuidV7.next()` in the constructor | none |
| `VersionedUuidEntity`, `VersionedEntity<ID>` | add `@Version Long version` (boxed `Long`) to `UuidEntity` or to `AuditedEntity<ID>`, so natural key tables like `stock_levels` and `store_settings` can be versioned too | `OptimisticLockingFailureException` on a stale save |
| `CurrencyCodeConverter` | JPA `AttributeConverter<CurrencyCode, String>` | none |

### Value sourcing

| Action | Value | Source |
|---|---|---|
| New entity | `id` | `UuidV7.next()` in the entity constructor |
| Any save | `created_at`, `updated_at` | Spring Data JPA auditing, `Instant` from the shared `Clock` bean (`@EnableJpaAuditing(dateTimeProviderRef = ...)`); `DEFAULT now()` for plain SQL inserts |
| Guest order link (slice 1) | signature | HMAC SHA256 of `orderId` + expiry time, key from `GUEST_ORDER_LINK_SECRET`; no column |
| Any save | `created_by`, `updated_by` | `AuditorAware<UUID>` in `shared`: returns empty (null) until feature 4 adds Spring Security, then the token's `sub` |
| Any versioned save | `version` | JPA `@Version` |
| Money rounding | decimals | `java.util.Currency.getDefaultFractionDigits()` via `CurrencyCode.decimals()` |
| New product / order line | `seller_id` | `SellerId.HOUSE` constant |
| Order number (slice 1) | counter | `nextval('ordering.order_number_seq')`; prefix from `store_settings.order_number_prefix`; formatted as `prefix-` + counter padded to 6 digits |
| Order line snapshot | `sku`, `product_name`, `options_label` | `CatalogApi` at checkout |
| Checkout | `contact_email` | the guest's checkout input, or the logged in token's email claim |
| Checkout | `cart_id`, `create_account_requested` | the current cart; the checkout request's "create my account" box |
| Emails (notification) | recipient, order number, ship name, lines, totals, tracking info, refund amount and reference, guest link | the event payload only: `OrderPaid`, `OrderShipped`, `OrderRefunded`, `AccountCreatedFromCheckout` carry them (see *Event payloads*) |

### Key invariants

- No foreign key or join crosses a schema.
- All amounts in one row share that row's `currency`. `Money` refuses to mix currencies.
- `0 <= reserved <= on_hand` for every stock row.
- One active cart per customer; a cart has exactly one owner (customer or guest token).
- One live review per customer per product; one success per order in payments; one usage per order per coupon.
- `sum(order_refunds.amount) <= orders.grand_total`, checked in the ordering domain while holding the order row lock.
- `orders.items_total = sum(order_lines.line_subtotal)`, `discount_total = sum(line_discount)`, `tax_total = sum(line_tax)` (rounding is per line).

### Flow rules the slices must follow

These come from the cross check. They are not new tables, but each slice must build them this way.

| Flow | Rule | Slice |
|---|---|---|
| Sellable | A variant is sellable when `active`, its product is `active`, neither the variant, the product nor the product's category is archived, and `price_currency` equals the store currency. A currency change needs no flag; unpriced items show as "not available" in the cart. | 5, 10 |
| Checkout replay | Same `(cart_id, idempotency_key)` + same `request_hash` returns the same order. Same key with a different hash returns 409 `IDEMPOTENCY_KEY_REUSED`. The cart stays `ACTIVE` until the order is paid. | 5 |
| Stock reserve | Lock the needed `stock_levels` rows with `FOR UPDATE`, sorted by `variant_id` (stops deadlocks). Reserve raises `reserved`. Confirm lowers `on_hand` and `reserved` and writes a `SALE` movement. Release and expiry lower `reserved` only. Only `orders.expires_at` decides expiry; inventory waits for `OrderExpired`. A confirm on a reservation that is no longer `ACTIVE` is skipped (payment then ends as `CAPTURED_UNAPPLIED`, since ordering already refused it). | 5, 9 |
| Stock edge event | `StockLevelChanged` is sent when `available` crosses zero, computed from the before and after values in the same transaction. | 6 |
| Guest order link | `GET /guest/orders/{id}?sig=` uses the order UUID, never the order number. | 5 |
| Link guest orders | On every login whose token says the email is verified (cheap and idempotent): `UPDATE orders SET customer_id = :sub WHERE customer_id IS NULL AND lower(contact_email) = lower(:email) RETURNING id`. If any rows changed, publish `GuestOrdersLinked(customerId, orderIds)`; review asks the ordering API which of them were delivered and adds eligibilities. Set `users.guest_orders_linked_at`. | 8 |
| Create my account | `OrderPaid` carries `createAccountRequested`. Identity creates the Keycloak user; a 409 (user exists) is treated as done, and it looks up the existing user by email to get the `sub`. Then publishes `AccountCreatedFromCheckout`. | 8 |
| Clear cart on paid | `OrderPaid` carries `cartId` and the lines. Cart lowers each item by the ordered quantity (deleting it at 0), so items added during payment stay; the cart becomes `CONVERTED` only if it is then empty, otherwise it stays `ACTIVE`. | 5 |
| Payment retry | `POST /payments/{orderId}/retry` takes an `Idempotency-Key`. The old open attempt moves to `FAILED` first, then a new one is created. | 9 |
| Admin cancels a paid order | `OrderCancelled` carries `wasPaid`. Inventory returns the stock (`RETURNED`); the coupon stays `REDEEMED`. | 10 |
| Search freshness | The search document stores the source version of each part (stock, price, rating) and ignores an event with an older version. A rebuild reads current state, so it never goes backwards. `StockLevelChanged` carries `productId`. | 6 |
| Manual refund | Allowed only for an order that was paid and is now `CANCELLED`. Recording one refund with the full `grand_total` moves the order to `REFUNDED`; phase 1 records only full refunds. | 13 |
| Currency change | The settings update that changes `currency` must include the new `flat_shipping_fee` and `free_shipping_above` in the same request. Coupons in the old currency stop working until edited. | 10 |
| Account deletion | Personal columns are overwritten with placeholders (e.g. `deleted-<id>@invalid`, `Deleted user`), never set to null, so NOT NULL and CHECK rules still hold. Completed event publications are purged on the normal 7 day job. | 16 |
| Cart merge | Done in one transaction holding a lock on the guest cart; the customer's active cart is created with `INSERT ... ON CONFLICT DO NOTHING`, then reread. Larger quantity wins. Guest cart becomes `MERGED` with a null token. | 8 |
| Coupon reserve | Lock the coupon row (`FOR UPDATE`), count only `RESERVED` and `REDEEMED` usages for total and per customer limits, then insert. | 14 |
| Rating summary | A review counts when `NOT hidden AND archived_at IS NULL`. The summary changes by delta (`SET rating_sum = rating_sum + :d, review_count = review_count + :c`) in the same transaction, only when the counted flag flips or the rating changes while counted. So archiving a hidden review subtracts nothing. | 15 |

### Event payloads (what each event must carry)

| Event | Payload |
|---|---|
| `OrderPaid` | `eventId`, `orderId`, `orderNumber`, `customerId` (null for guest), `contactEmail`, `cartId`, `createAccountRequested`, ship name, lines (`variantId`, `productId`, `sku`, `productName`, `optionsLabel`, `quantity`, `unitPrice`, `lineTotal`), totals, `currency`, signed guest link (guest only) |
| `OrderCancelled` | `orderId`, `orderNumber`, `contactEmail`, `wasPaid`, `reason` |
| `OrderExpired` | `orderId` |
| `OrderShipped` (as `OrderStatusChanged` to `SHIPPED`) | `orderId`, `orderNumber`, `contactEmail`, `carrier`, `trackingNumber`, `trackingUrl` |
| `OrderStatusChanged` to `DELIVERED` | `orderId`, `customerId`, `productIds` (for review eligibility) |
| `OrderRefunded` | `orderId`, `orderNumber`, `contactEmail`, `amount`, `currency`, `reference`, `refundedAt` |
| `PaymentSucceeded` / `PaymentFailed` | `orderId`, `attemptId`, `providerPaymentId`, `amount`, `currency` |
| `PaymentNeedsRefund` | `orderId`, `attemptId`, `providerPaymentId`, `amount`, `currency` |
| `GuestOrdersLinked` | `customerId`, `orderIds` |
| `StockLevelChanged` | `variantId`, `productId`, `inStock`, `version` |
| `ProductRatingChanged` | `productId`, `reviewCount`, `ratingSum`, `updatedAt` |

Every listener stays idempotent through a unique key: `stock_reservations.order_id` + final states, `stock_movements (order_id, type)`, `coupon_usages.order_id` + status, `email_deliveries.source_key`, `review_eligibilities` PK, `payment` `webhook_events` and `provider_payments` UQs, and the order status self move rule.
- The order number counter never restarts, even when the prefix changes.

### Security model

- Each module reads and writes only its own schema (locked). Data from another module comes through its `api`.
- Personal data lives in: `identity.users` (email), `customer.*` (name, phone, addresses), `ordering.orders` (guest email, ship address), `promotion.coupon_usages` (guest email), `notification.email_deliveries` (recipient), `payment.webhook_events.payload`. Slice 10 (privacy) must remove or anonymize these on account deletion, including archived rows. Plain columns make that a simple update.
- `cart.guest_token` is stored raw (owner's choice). Anyone with read access to the database can take over a guest cart. Carts hold no personal data, so the impact is low.

### Configuration required

- `spring.jpa.properties.hibernate.jdbc.time_zone: UTC` in `application.yml` (this feature).
- `GUEST_ORDER_LINK_SECRET`: key for signing guest order links (added in slice 1, feature 5; goes in `.env.example`).

### Critical test scenarios

- Happy path: wipe the local database volume, start the app; Flyway runs every migration, the sequence exists, the app starts with `validate`. Verifies **AC-5**.
- Failure case: `Money.of(10, INR).plus(Money.of(1, USD))` throws; `Money.of(1.005, INR).rounded()` is `1.01`; JPY rounds to whole numbers. Verifies **AC-1**.
- Insert path: saving a new entity built on `UuidEntity` runs one `INSERT`, and saving a loaded one runs one `UPDATE` (not a second insert) (SQL log), with a version 7 id and filled `created_at`. Verifies **AC-2**, **AC-4**.

### Slice handoff (who creates which tables)

| Feature | Creates |
|---|---|
| 4 Security & login | `identity.users` |
| 5 Core buying loop | `catalog.categories`, `products`, `variants`; `inventory.*`; `cart.carts`, `cart_items`; `ordering.orders`, `order_lines`, `order_status_history`, `idempotency_keys`; `payment.*`; `notification.email_deliveries`; `settings.store_settings` with its default row; local seed data in `db/seed/local` (added to `flyway.locations` only in `application-local.yml`) |
| 6 Product search | `search.index_rebuilds` |
| 7 Variants & images | `catalog.option_types`, `option_values`, `product_option_types`, `variant_option_values`, `product_images` |
| 8 Customer accounts | `customer.customer_profiles`, `addresses`, `wishlist_items` |
| 12 Shipping info | `ordering.shipments` |
| 13 Manual refund | `ordering.order_refunds` |
| 14 Basic coupons | `promotion.*`; `cart.carts.coupon_code` column |
| 15 Ratings & reviews | `review.*` |
| 16 Privacy | `customer.consents` |

Each migration follows the AGENTS.md naming (`V<block>_<n>__<module>_<what>.sql`). A slice that needs to change this model goes back through `/architect`.

## Build plan

Tracer Bullet: this feature builds only the shared base that the first real thread (feature 5) runs on, then proves it on an empty database.

1. Add `UuidV7` and the typed ID records (with `SellerId.HOUSE`) in `com.kmicro.shared`, satisfies **AC-2**, **AC-3**
2. Add `CurrencyCode`, `Money` (rounding, currency mismatch error) and `CurrencyCodeConverter`, satisfies **AC-1**
3. Add `AuditedEntity<ID>`, `UuidEntity`, and the two versioned variants (`@MappedSuperclass`, `Persistable` with the transient `isNew` flag flipped in `@PostLoad`/`@PostPersist`, boxed `Long` version), JPA auditing config using the shared `Clock`, and an `AuditorAware<UUID>` that returns empty for now; set `hibernate.jdbc.time_zone: UTC`, satisfies **AC-4**
4. Add `db/migration/ordering/V7_2__ordering_order_number_sequence.sql` creating `ordering.order_number_seq`, and `db/migration/modulith/V0_2__modulith_shedlock.sql` creating `public.shedlock`, satisfies **AC-5**
5. Run `./mvnw spotless:apply` and `./mvnw verify`; wipe the local Postgres volume and start the app to prove Flyway runs from empty, satisfies **AC-5**, **AC-6**
6. Review the *Later phase readiness* and *Slice handoff* tables with the owner before feature 5 starts, satisfies **AC-7**

## Consequences

**Positive**:
- Every slice builds tables to one agreed set of rules; no slice invents its own money, ID or audit style.
- Later phases are additive; no data moves.
- Keying by `variant_id` means SKU renames never break carts, stock or wishlists.

**Negative / tradeoffs**:
- The design doc says modules "work with SKUs". This spec changes that to `variant_id`, so `CatalogApi`, `InventoryApi`, the cart and wishlist endpoints (`/cart/items/{variantId}`) and both design docs need updating.
- Archived reviews and archived addresses still hold personal data until slice 10 handles deletion.
- Only 4 tables have `version`; other admin edits (coupons, categories) can overwrite each other silently.
- Raw guest cart tokens in the database (see Security model).
- `created_by` is empty for everything written before feature 4 lands.

**Neutral**:
- Tables arrive slice by slice, so the database is not "complete" until slice 10.
- A custom `UuidV7` helper is ours to keep correct (about 20 lines, plus one check).

## Follow-up

- [ ] Update `docs/design/backend-design.md` (Modules, Variants, Main endpoints) and `docs/design/frontend-design.md` to say other modules and the cart API use `variantId`, not SKU.
- [ ] Add to the design doc: the new events (`PaymentNeedsRefund`, `GuestOrdersLinked`, `OrderRefunded`), the `CAPTURED_UNAPPLIED` payment state with its admin list, and the `RETURNED` stock state.
- [ ] Decide how long `notification.email_deliveries` keeps recipient emails (slice 10, privacy).
- [ ] Consider `version` on `promotion.coupons` and `catalog.categories` when feature 10 (safe concurrent edits) is designed.
- [ ] Feature 4: make `AuditorAware` return the token's `sub`.

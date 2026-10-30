# Scope: KMicro backend

An ecommerce backend built as a Spring Modulith. It serves the KMicro web shop now and a mobile app later. Shoppers browse, search, buy as guests or with an account, and pay with Razorpay. An admin runs the catalog, stock, orders and store settings. Every locked decision lives in `docs/design/backend-design.md`; this scope only orders the work.

**Build approach:** Tracer Bullet (prove one thin real path through every layer first, then thicken one part at a time).
**Workflow:** Alpha for every feature (after `/develop`, run `/check verify`). No test first approach and no `/test` step: you build, then check the real behavior. Only when the output is wrong, `/debug` fixes it and adds one regression test for that bug. `/architect` is the recommended first stop for a feature with a real decision, but skippable when the design doc already settles the build. Any feature can carry its own tag to do more or less.
**Target:** tester launch on 13 Nov 2026 (2 weeks), after slice 6 plus observability. This is a stretch for one developer. Slices 7 to 11 finish phase 1 after testers start.
**Phase 1 is a success when:** most testers find a product, check out, pay (test mode) and get the email with no help and no errors, and the admin can add products, manage stock, ship and refund orders without touching the database.

_These are recommendations to keep your build orderly, not requirements. Skip anything that does not fit: if you already know how to build a feature, use `/develop` and skip `/architect`. You decide when a feature is `done`._

_The web screens for each slice are built in the kmicro-frontend repo (see `docs/design/frontend-design.md`). This scope covers the backend only. Until a screen exists, you can check a slice through Swagger UI or Postman._

## At a glance

| # | Feature | Phase | Status |
|---|---------|-------|--------|
| 1 | Stack & architecture | Foundation | in-progress |
| 2 | Coding standards & tooling | Foundation | planned |
| 3 | Data model | Foundation | planned |
| 4 | Security & login foundation | Foundation | planned |
| 5 | Core buying loop | Slice 1 | planned |
| 6 | Product search | Slice 2 | planned |
| 7 | Variants & product images | Slice 3 | planned |
| 8 | Customer accounts | Slice 4 | planned |
| 9 | Razorpay payments & order expiry | Slice 5 | planned |
| 10 | Admin shop operations | Slice 6 | planned |
| 11 | Observability | Slice 6 | planned |
| 12 | Basic shipping info | Slice 7 | planned |
| 13 | Manual refund record | Slice 7 | planned |
| 14 | Basic coupons | Slice 8 | planned |
| 15 | Ratings & reviews | Slice 9 | planned |
| 16 | Privacy & legal basics | Slice 10 | planned |
| 17 | Kafka event publishing | Slice 11 | planned |

## Foundations

### 1. Stack & architecture · in-progress
Record the locked stack and module layout from the design doc as a spec, then scaffold a runnable Spring Modulith app with the local Docker Compose core profile.
**Done when:** the stack and module rules are recorded in a spec; the empty app starts from IntelliJ with Postgres, Redis, Keycloak, Meilisearch and Mailpit started for you; the Spring Modulith boundary test passes.
spec [0001](../specs/0001-stack-architecture/index.md) · code in `pom.xml`, `compose.yaml`, `src/main/java/com/kmicro/`
- [x] Decide the stack (spec): `/architect stack & architecture`
- [x] Scaffold from the decision: `/develop stack & architecture`
- [ ] Verify it: `/check verify stack & architecture`

### 2. Coding standards & tooling
Capture the real conventions and tooling from the scaffolded project, then install formatting, static checks, dependency scanning and pinned versions.
**Done when:** root `AGENTS.md` lists the real build and test commands and conventions, and the format, check and dependency scan steps run clean.
- [ ] Capture conventions + tooling choices: `/audit`

### 3. Data model · needs a decision
One schema per module for every phase 1 module, money as amount plus currency, UUID keys, order number sequence, `sellerId` ready for later, and no links across schemas.
**Done when:** Flyway creates every phase 1 schema from empty, and the model supports later phases (sellers, partial refunds, abandoned cart) without a breaking migration.
- [ ] Design it (spec): `/architect data model`

### 4. Security & login foundation · needs a decision
Keycloak realm as code, web login through the backend (BFF) with a Redis session cookie and CSRF protection, Bearer tokens for mobile, roles, the shared error format and rate limiting.
**Done when:** a test admin and a test customer can log in through `/bff/login`; `/bff/me` shows their roles; admin endpoints reject customers; Bearer tokens work for the same endpoints; changes without a CSRF token are refused.
- [ ] Design it (spec): `/architect security & login foundation`

## Slice 1: Core buying loop

### 5. Core buying loop · needs a decision
The thinnest real thread through the whole shop, as a guest: admin adds a simple product with price and stock, the shopper lists products, adds to a cart (cart token), checks out with an email and address, pays with the fake provider, the order becomes paid, stock is confirmed and a confirmation email arrives. Store settings start with currency, tax and a flat shipping fee. This slice is the walking skeleton.
**Done when:** one guest order goes from empty cart to paid with server side prices, tax and shipping; a repeated checkout request with the same key makes one order; the confirmation email shows in Mailpit; the module boundary test still passes.
- [ ] Design it (spec): `/architect core buying loop`

## Slice 2: Product search

### 6. Product search · needs a decision
Keep a Meilisearch product index in sync from catalog and stock events, and serve search with text, category, price and in stock filters, plus sorting.
**Done when:** a product change shows in search within seconds; filters and sort work; the admin can rebuild the index; category browsing still works when Meilisearch is down.
- [ ] Design it (spec): `/architect product search`

## Slice 3: Variants & product images

### 7. Variants & product images · needs a decision
Admin defined option types (Size, Color, Weight, anything) and values; each variant is its own SKU with price, stock and images; image upload through the file storage interface.
**Done when:** the admin creates option types and a product with variants; each variant can be priced, stocked, added to cart and bought; images upload to the local folder or S3 and show by key; search filters by option.
- [ ] Design it (spec): `/architect variants & product images`

## Slice 4: Customer accounts

### 8. Customer accounts · needs a decision
Logged in shopping: profile, addresses, wishlist, guest cart merge on login, my orders, guest order link by email, and "create my account" at checkout.
**Done when:** a customer sees only their own orders and addresses (others give 404); the guest cart merges on login; the guest order link works and expires; ticking "create my account" creates a Keycloak user and sends the set password email; past guest orders link after the first verified login.
- [ ] Design it (spec): `/architect customer accounts`

## Slice 5: Razorpay payments & order expiry

### 9. Razorpay payments & order expiry · needs a decision
Real Razorpay test mode payments behind the payment provider interface, signed webhooks as the source of truth, payment retry, and expiry of unpaid orders with stock release.
**Done when:** a test card payment marks the order paid only after the signed webhook; a repeated webhook changes nothing; a failed payment can be retried; an unpaid order expires on time and its stock comes back.
- [ ] Design it (spec): `/architect razorpay payments & order expiry`

## Slice 6: Admin shop operations (tester launch)

### 10. Admin shop operations · needs a decision
What the admin needs to run the shop: categories, stock adjustments, order list and allowed status moves, full store settings (tax mode, free shipping amount, order number prefix, currency change and repricing), and safe concurrent edits.
**Done when:** the admin can do every phase 1 task through the API without the database; disallowed status moves are refused; changing the currency hides unpriced products until repriced; two admins editing the same record cannot overwrite each other.
- [ ] Design it (spec): `/architect admin shop operations`

### 11. Observability
Traces, JSON logs with trace and module names, health checks and the business metrics from the design doc, sent to the existing Grafana stack.
**Done when:** one checkout shows as one trace across modules in Grafana; failed payments and stuck events show as metrics; readiness fails when the database is down but not when search is down.
- [ ] Build it: `/develop observability`

## Slice 7: Shipping info & refunds

### 12. Basic shipping info
When the admin ships an order, they add the carrier, tracking number and an optional tracking link; the customer gets a shipped email and sees it on the order.
**Done when:** shipping an order requires the carrier and tracking number; the shipped email arrives; the info shows on the customer's and guest's order view.
- [ ] Build it: `/develop basic shipping info`

### 13. Manual refund record
The admin cancels a paid order, refunds in the Razorpay dashboard, then records the refund reference and date, which marks the order refunded.
**Done when:** only cancelled orders can be marked refunded; the reference and date are stored; the customer gets a refund email; nothing calls the Razorpay refund API.
- [ ] Build it: `/develop manual refund record`

## Slice 8: Basic coupons

### 14. Basic coupons · needs a decision
Admin made codes with percent or fixed discount, minimum order, dates, total and per customer limits, one per order, reserved at checkout and counted when paid.
**Done when:** a valid code changes the totals in the order items, discount, tax, shipping; invalid codes give a clear reason; two shoppers cannot both take the last use; expired or cancelled orders free their coupon; coupon checks are rate limited.
- [ ] Design it (spec): `/architect basic coupons`

## Slice 9: Ratings & reviews

### 15. Ratings & reviews · needs a decision
Verified buyers rate 1 to 5 stars with optional plain text, one review per product, editable; the admin can hide reviews; the average and count feed search.
**Done when:** only customers with a delivered order for the product can review; one review per customer per product; hidden reviews disappear from the product; search sorts and filters by rating.
- [ ] Design it (spec): `/architect ratings & reviews`

## Slice 10: Privacy & legal basics

### 16. Privacy & legal basics · needs a decision
Privacy policy and terms pages served by the API, customer data export and account deletion across modules, and stored consent for marketing emails.
**Done when:** a customer can download their data and delete their account; deletion removes or anonymizes personal data in every module while keeping order records the law needs; consent is recorded with time and version.
- [ ] Design it (spec): `/architect privacy & legal basics`

## Slice 11: Kafka event publishing

### 17. Kafka event publishing
Send the selected order, payment and catalog events to Kafka topics, documented with Springwolf, with the app still working when the messaging profile is off.
**Done when:** the listed events reach their topics with the agreed message format and keys; topics are created by the app; turning Kafka off does not break any flow.
- [ ] Build it: `/develop kafka event publishing`

## Deferred
Out of scope for phase 1, kept so the plan stays honest.
- **Mobile app support**: phase 2; no new backend features expected, only fixes the app surfaces · needs a decision
- **Refunds through the Razorpay API (full and partial)**: phase 3; approach already noted in the design doc, section 16 · needs a decision
- **Carrier integration**: live rates, labels, tracking updates · needs a decision
- **Abandoned cart reminders**: email and push · needs a decision
- **Multi vendor marketplace**: sellers, sub orders, payouts · needs a decision
- **Richer promotions**: automatic deals, product or category coupons, free shipping coupons · needs a decision
- **Review replies and photos** · needs a decision
- **Google and Facebook login**: Keycloak setup only, no app code · needs a decision
- **Admin editable email templates**: admin writes email templates in the UI, saved in the database with placeholders, used by notification; phase not set yet · from spec 0001 · needs a decision

## Legend

**The decision box.** Every feature carries exactly one, the sub task whose label ends with `(spec)`. Its wording varies (`Design it (spec)` normally, `Decide the stack (spec)` on Stack & architecture), so skills locate it by that `(spec)` suffix, never by an exact label. Every other box is an execution box and `/architect` never ticks one.

**Feature lifecycle**: the scope updates as a feature moves; each row is what it shows and who sets it:

| State | Set by | The feature shows |
|---|---|---|
| `planned` · needs a decision | `/scope` | one box: `Design it (spec): /architect <feature>` |
| `in-progress` (designed) | **`/architect` at spec capture** | `Design it` ticked; spec linked; `Build it: /develop <feature>` + **2 to 5 milestones**; the tier's closing boxes (`Verify it` Alpha+, `Test it` Beta+, `Review it` + `Document it` GA); any surfaced follow up enrolled |
| `in-progress` (building) | `/develop` | milestone sub boxes tick one by one; code pointer filled |
| `in-progress` (verified) | `/check verify` | `Build it` + milestones ticked; `Verify it` ticked |
| `done` | **you, when you decide it is** (any skill sets it when you say so); `/sync` reconciles | boxes you ran ticked, skipped ones marked skipped; the tier's last stage (`Prototype` → after `/develop`; `Alpha` → after `/check verify`; `Beta`/`GA` → after `/test`) is the suggested point to call it done; `/sync` captures conventions |

- **Next step** = the first unticked box (always a command or a tracked milestone).
- **needs a decision** = run `/architect` first; otherwise straight to `/develop` (or `/audit` for standards & tooling). The tag drops once the spec is captured.
- **Atomic build tasks live in the spec's `## Build plan`, not here**: the scope carries only the milestone rollup.
- **Status** `planned` → `in-progress` → `done`, plus `existing` (pre workflow) and `dropped` (removed from scope, kept for history).
- **Approach tag** beside a heading (e.g. `· Facade`) overrides the project default for that feature; no tag = inherits it.
- **Workflow tier tag** beside a heading (e.g. `· Beta`) sets that one feature's rigor above or below the project default; no tag inherits the default. It decides the feature's check boxes and each skill's next suggestion.
- **Workflow** (header line) is the project default, what runs after `/develop`: **Prototype** = nothing (trust develop's own build time self check); **Alpha** = `/check verify`; **Beta** = `/check verify` then `/test`; **GA** = adds a fresh model `/check review` then `/document`. A feature built on an unratified decision (an `Assumed` spec) stays flagged, but that never blocks `done`.
- **Pointer line** (`spec <n> · code in <path>`): the spec link added by `/architect`, the code path by `/develop`.

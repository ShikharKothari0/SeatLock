# Event management and authoritative pricing

Implemented 18 October 2026. This milestone adds administrator-created venue layouts/events and server-owned INR quotes. Payments remain simulated; no gateway or actual charge is involved.

## Admin workflow

Sign in with an ADMIN account and open `/admin/catalog` (Events & venues). Administrator provisioning remains documented in [authentication.md](authentication.md); registration never grants that role.

1. Create a venue with name, city, address, IANA timezone and sections. Each section has a unique name, an alphanumeric seat prefix and seat count. The generator creates reusable seats such as P1, P2 and P3. Limits are 20 sections, 1,000 seats per section and 5,000 seats per venue.
2. Choose the venue, enter event details and sales dates, and set one price for every section plus an optional fixed fee per booking. UI amounts are rupees with at most two decimals; the backend stores integer paise and rejects omitted section amounts or fractional paise. Dates entered in the form use the browser's explicitly displayed timezone and are sent as UTC instants. The stored venue timezone does not implicitly reinterpret those fields.
3. Review the draft's section prices and fee in the event list, then publish. Drafts are absent from public event lists and return 404 for public event, seat and price lookup. Their seats cannot be held.
4. Close sales when needed. This stops new holds, quotes and confirmations. Existing bookings and receipts remain accessible. Active holders can release seats or let the holds expire.

Each event copies its venue layout into independent seat inventory with new IDs. Booking a physical seat at one event does not allocate it for another event. Catalog creation/publication/closure records the administrator UUID, action, resource and timestamp in `catalog_audit`.

This first workflow supports creation, publication and sales closure. Editing layouts/prices/events, deleting drafts, CSV import, operational seat blocking and event cancellation/refunds remain future work. Closing sales is not cancellation and does not change confirmed bookings.

## Pricing and confirmation

- Public `GET /api/events/{id}/prices` returns server-configured section prices and booking fee. The selection screen shows an estimate from those values.
- Authenticated `POST /api/bookings/quote` accepts `seatIds`. It locks the seats, verifies one event, active ownership, hold generation, Redis TTL and the sales window, then saves an immutable quote.
- Quotes expire after at most two minutes, bounded by the earliest held-seat deadline. The frontend uses server time to account for client clock skew. Refreshing a quote does not extend the seat hold.
- Checkout and demo payment display the quote's itemized subtotal, fee, currency and total. No tax is applied in this personal-project demo; this is not a production tax calculation.
- `POST /api/bookings/confirm` now requires `seatIds`, `idempotencyKey` and `quoteId`. Caller-supplied user IDs or totals cannot determine ownership or price. Confirmation checks the same user, exact seat set, quote deadline and hold generation, and still checks current sales/hold validity.
- A quote is consumed at most once. The booking and its immutable price snapshot commit together with the existing transactional outbox work. A retry with the same key and quote returns the original booking even after its original quote deadline. Reusing the key with another quote or payload is rejected.
- `GET /api/bookings/{id}/price` is owner-checked and returns the accepted snapshot. Receipts use it rather than recalculating from today's prices. Historical bookings without a snapshot show “Price snapshot unavailable”; their amounts are not invented.

Prices are INR-only in this milestone. An issued quote is a short-lived price commitment: if prices are changed administratively in the future, an already issued valid quote is honored. Published-price editing is not currently exposed. A sales closure still blocks an unconfirmed quote.

## Migration and compatibility

Flyway V7 adds venue metadata/layouts, event publication/sales-end/fee fields, section prices, quotes, booking price snapshots and catalog audit records. Existing inventory receives an explicit demo price of INR 500 per seat and zero booking fee; historical bookings receive no fabricated price. Existing events retain public visibility, but their actual sales/event dates are now enforced. Past events cannot accept new holds.

Legacy venues have no reusable layout automatically inferred from event inventory. Create a new venue/layout through the admin UI to use the new event generator. Existing events and bookings are retained. Scripts and API clients must obtain a quote before confirmation. Shared deployments should coordinate the frontend/backend rollout; old clients without a quote cannot create new bookings.

Quote and audit retention is not automated. Any future cleanup must retain quotes referenced by `booking_price` and preserve booking snapshots. The current catalog list is intended for a small personal project and is not paginated.

## Validation results — 18 October 2026

- Backend reservation/authentication/catalog suite: 25 tests passed. After tightening catalog JSON validation, all 7 catalog/pricing tests passed, including the new missing/fractional-price regression (26 distinct tests across these runs).
- Frontend build and lint passed; 17 unit tests and 8 controlled-browser scenarios passed.
- Live catalog workflow: 4 scenarios passed, then passed again after Docker recovery and a fresh stack startup, including UI venue/event creation and publication, priced checkout and refreshed receipt, tampered-total protection, idempotent replay and sales closure.
- Live reservation recovery: 6 scenarios passed against the running services.
- Full live booking regression: 10 scenarios passed, including natural five-minute Redis/PostgreSQL expiry and Kafka hold/confirmation/release events.

## Validation commands

Java 21 and Docker, from the repository root:

```powershell
.\mvnw.cmd '-Dtest=CatalogPricingTest,AuthenticationTest,ReservationHardeningTest,ReservationContractTest' test
```

From frontend/:

```powershell
npm run build
npm run lint
npm test
$env:BROWSER_CHANNEL = 'msedge'
npm run test:e2e
$env:SEATLOCK_LIVE_TEST = '1'
npm run test:catalog
npm run test:recovery
npm run test:live
```

The live catalog suite creates dedicated accounts, a venue and event through the actual admin UI, publishes it, books it through the customer UI, verifies a refreshed price receipt, rejects a forged total and closes sales. It retains its dedicated records. Reports/screenshots are ignored under frontend/test-results/ and execution logs are under target/catalog-*.log.

The backend payment simulation milestone is now implemented; see [demo payments](demo-payments.md) for explicit attempt states, success/failure/delay outcomes and validation. A gateway sandbox remains optional.

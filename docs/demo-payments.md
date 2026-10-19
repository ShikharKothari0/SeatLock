# Durable demo payments

Implemented and validated 19 October 2026. This is a simulator: no payment gateway is contacted, no money moves, and no card or bank details are collected.

## Customer flow

At checkout, review the price and continue to payment. Choose a display method (UPI, Card or Net banking) and a demo outcome:

- Successful payment: confirm the booking immediately.
- Declined payment: record a failed attempt without booking seats. Return to checkout for a fresh total and a new attempt; the original seat reservation is not extended.
- Delayed payment: remain pending for about 15 seconds, then attempt confirmation. The page checks automatically and can be refreshed safely.

The pending request, including its method, outcome and idempotency key, survives refresh in the current tab. A lost response retries that same attempt. A completed booking remains available through account history even if browser storage is cleared. A dedicated cross-device pending-payment list is not implemented.

## Backend guarantees

Flyway V8 adds `payment_attempt`. Each attempt belongs to an authenticated user and one checkout quote. The amount and currency come from the stored quote, not request fields. States are PENDING, SUCCEEDED, FAILED and EXPIRED; terminal outcomes do not change when replayed.

One quote can be used for only one payment attempt. Reusing an idempotency key requires the same user, quote, seat set, payment method and demo outcome. Retrying a declined payment requires a new quote and key.

Processing locks the payment row and reuses reservation validation. Successful payment status, booking, accepted price and transactional outbox writes commit together. A database failure rolls all of them back. Infrastructure errors leave the attempt pending for another try. Business rejection records failure only after the booking transaction has rolled back.

The payment deadline is the quote deadline (at most two minutes and never beyond the seat reservation). Delayed success after the deadline becomes EXPIRED and creates no booking. Closed sales or replaced/released seat reservations cannot be confirmed. This simulator validates availability before recording success; it does not model real money arriving after expiry or refunds.

A background worker checks up to 50 due attempts every two seconds. Attempts are stored in PostgreSQL, so the worker can resume pending work after a backend restart. Row locking and terminal-state checks make duplicate/concurrent processing safe. Multi-instance load testing and a restart-during-payment live test are not included in this validation pass.

## API and compatibility

All endpoints require authentication; writes also require CSRF protection.

- `POST /api/payments`: `quoteId`, `seatIds`, `idempotencyKey`, `method` (`UPI`, `CARD`, `NET_BANKING`), `scenario` (`SUCCESS`, `FAILURE`, `DELAYED_SUCCESS`). Returns a persisted attempt.
- `GET /api/payments/{id}`: owner-only status lookup, without processing an outcome.
- `POST /api/payments/{id}/process`: owner-only processing using the already saved outcome. The caller cannot change amount or outcome here.
- `POST /api/bookings/confirm`: retained for existing clients. New bookings use a recorded successful UPI demo payment. Replays of historical bookings still return their original receipt without inventing a past payment.

`seatlock.payments.demo-enabled=false` disables new attempts and background processing. `seatlock.payments.worker-enabled=false` disables the worker for deterministic tests. `seatlock.payments.poll-ms` defaults to 2000. Existing attempts remain readable; explicit processing of an existing attempt is still supported. A real provider integration would need a separate authenticated webhook flow and additional states, including refund/reconciliation handling.

## Validation

- The combined authentication/catalog/payment integration run passed 20 tests; a subsequent payment-only run passed all 9 tests including historical replay (21 distinct integration tests across these runs).
- Payment coverage includes exact amounts, owner/CSRF checks, declines, delayed worker completion, late expiry, closed sales, concurrent outcomes, idempotent replay and injected payment-write rollback.
- Frontend build, lint and 17 unit tests passed. All 12 controlled-browser scenarios passed, including decline and delayed refresh.
- Live payment validation passed 3 scenarios (decline then retry, delay with refresh, and late expiry); all 4 live catalog scenarios passed with payment-backed confirmation. The pending-payment screen was visually reviewed.
- Live validation results are recorded in `frontend/test-results/payment-validation.json` and `catalog-validation.json`; these generated files are ignored by Git.

Run `npm run test:payments` from `frontend` with `SEATLOCK_LIVE_TEST=1` and, on this machine, `BROWSER_CHANNEL=msedge`. It creates dedicated accounts and event data. Backend tests require Java 21 and Docker: `mvnw.cmd -Dtest=DemoPaymentTest,AuthenticationTest,CatalogPricingTest test`.

Do not run a Maven compile/test build against the same `target/classes` while a development backend is serving live validation. Restart it after compilation, or run an independently packaged application.

The next product improvement is richer event content, draft editing/preview and customer discovery. Persistent deployment storage, CI and operational monitoring remain separate unfinished milestones.

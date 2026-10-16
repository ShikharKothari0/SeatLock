# SeatLock project status

> **Latest milestone:** transaction/outbox hardening is implemented and covered by isolated failure/concurrency tests. See [transaction hardening](transaction-hardening.md) for current guarantees and limits. Earlier milestone descriptions below are historical; authenticated identity is next.


> **Subsequent milestone completed:** server-backed hold recovery/release, exact expiry and receipt/history retrieval are now implemented. See [reservation recovery](reservation-recovery.md) for the current contracts, checks and remaining boundaries. Earlier sections below record the preceding stabilization pass.

Reviewed 16 October 2026 against the local source, GitHub origin/master, the supplied ARCHITECTURE.md and FRONTEND-INTEGRATION-GUIDE.md. The documents describe intended architecture; implementation and test evidence determine the status below.

## Where the project stands

SeatLock has reached an integrated local booking-demo stage. The reservation engine and customer journey work together against PostgreSQL, Redis and Kafka. It is not yet a complete account-based ticketing product or a production-validated distributed system.

| Area | Current evidence | Remaining work |
| --- | --- | --- |
| Reservation backend | Generation-specific holds, transactional outbox, concurrent idempotency, commit-failure/outage tests and revisioned seat caching | Multi-instance/load validation, operational retry monitoring and retention |
| Customer frontend | Selection, partial holds, server-backed recovery/release/history/receipts and server expiry exercised in a real browser | Authenticated sessions and server-owned pricing |
| Admin frontend | Dashboard routes and API polling implemented; known placeholder values explicitly unavailable | Accurate health/connections/invalidation/rate measurements and Kafka monitoring |
| Metrics stream | Disconnect/reconnect and multiple subscriptions exercised after lifecycle repair | Broader operational/load validation; frontend currently uses polling |
| Identity and payments | Seeded demo identity and clearly labelled simulated payment | Authentication, authorization, and optional provider sandbox/webhooks |
| Delivery | Local Compose services and application run | Persistent database volume configuration, deployment images/proxy, secrets, CI, isolated integration suite and reproducible load evidence |

The original guide's unchecked lists are not a reliable completion checklist. Frontend blocks 1–10 exist and have been stabilized; block 0 still has instrumentation gaps and block 11 needs broader failure and deployment validation. Avoid assigning a completion percentage without first defining whether the target is a portfolio demo or a production ticketing service.

## Changes made

- Typed API responses and consistent errors; stable confirmation payload/idempotency keys across uncertain responses and refresh.
- Persisted tab state and server-returned receipts, partial-hold recovery, conservative expiry handling and corrected route transitions.
- Honest demo payment and operational panels; responsive navigation, mobile seat scrolling, working themes and lazy page loading.
- Explicit loopback CORS origins for local Vite requests. One SSE client now cancels its own subscription instead of shutting down the shared scheduler; application shutdown still stops the scheduler.
- Frontend regression/browser tests and a repeatable real-service validation script with retained, dedicated test records.

## Repository position

Local HEAD is a3a69d2 (Frontend Integration). Fetched origin/master is d944277 (Update README.md); the committed difference is README.md only. These repairs are local uncommitted changes; they have not been pushed to GitHub.

## What to do next

The reviewed reservation correctness gaps now have implementations and targeted failure/concurrency coverage; see transaction-hardening.md. The next product step is authenticated identity and authorization for reservation and admin endpoints. Frontend guards do not secure direct API calls.

1. Add authoritative reservation APIs: server expiry, active holds, owner-checked release, booking lookup and paginated history. Connect the frontend to them so a closed tab does not lose access to a booking.
2. Harden transaction boundaries and recovery: outbox publication, owner-token-safe release, after-commit cache invalidation, duplicate/stale event handling and concurrency/failure tests.
3. Replace remaining dashboard placeholders with measured values and validate dependency-outage reporting.
4. Add server-derived identity and customer/admin authorization before sharing the application publicly.
5. Decide whether clearly simulated payment meets the portfolio goal; otherwise implement a provider sandbox with verified, idempotent webhooks and server pricing.
6. Make tests isolated and automatic, correct load-test assumptions, and package a persistent, documented deployment with restart/restore checks.

See [frontend stabilization and validation](frontend-stabilization.md) for test boundaries and [the ordered roadmap](end-to-end-roadmap.md) for contracts and milestone exit criteria.

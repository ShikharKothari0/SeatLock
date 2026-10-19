# SeatLock project status

> **Current objective:** temporary public demo, recording/screenshots and portfolio evidence, followed by shutdown. Vercel/Render/Aiven preparation is in progress; provider accounts and public validation are still required. See [temporary demo runbook](temporary-demo.md). Local rehearsal must not be described as public deployment.

> **Deployment packaging milestone (19 October 2026):** backend/frontend images and a separate private-service Compose stack are implemented. Five live booking checks plus HTTP/local-HTTPS security checks passed. See [deployment](deployment.md). Public hosting/domain TLS, CI, off-host backups and operational validation remain open.

> **Database persistence milestone (19 October 2026):** the existing PostgreSQL volume is explicitly retained, binary backups and isolated restore verification are implemented, and container recreation preserved business-record fingerprints. See [database recovery](database-recovery.md). Deployment packaging is now locally verified; next are CI/release checks and real-host validation.

> **Event catalogue milestone (19 October 2026):** richer event content, private draft editing/preview and customer search/city/category/date filters are implemented. Published events are protected from editing. See [event catalogue](event-catalogue.md) for validation and boundaries.

> **Payment milestone (19 October 2026):** durable demo payment attempts, success/decline/delay outcomes, background processing and safe retries are implemented. See [demo payments](demo-payments.md) for contracts, validation and limits. No real money is charged.


> **Catalog/pricing milestone:** admin venue/layout creation, draft event publication, sales closure and server-owned price quotes are implemented. See [catalog and pricing](catalog-and-pricing.md) for current contracts, limits and validation. Confirmation now requires a quote ID; older contract descriptions below are historical.


> **Completed authentication milestone:** authenticated accounts, session-based reservation ownership and administrator authorization are implemented. See [authentication](authentication.md) for setup, current API contracts, validation and deployment limits. Older milestone descriptions below are historical. Server-owned pricing is covered by the catalog milestone above.


> **Previous milestone:** transaction/outbox hardening is implemented and covered by isolated failure/concurrency tests. See [transaction hardening](transaction-hardening.md) for current guarantees and limits. Earlier milestone descriptions below are historical; authentication is covered by the current milestone above.


> **Subsequent milestone completed:** server-backed hold recovery/release, exact expiry and receipt/history retrieval are now implemented. See [reservation recovery](reservation-recovery.md) for the current contracts, checks and remaining boundaries. Earlier sections below record the preceding stabilization pass.

Reviewed 16 October 2026 against the local source, GitHub origin/master, the supplied ARCHITECTURE.md and FRONTEND-INTEGRATION-GUIDE.md. The documents describe intended architecture; implementation and test evidence determine the status below.

## Where the project stands

Updated 19 October 2026. SeatLock has reached an authenticated local booking-demo stage with administrator-managed events and authoritative prices. The reservation engine and customer journey work together against PostgreSQL, Redis and Kafka. Account registration, sign-in and ownership enforcement now work end to end. Real payments and production deployment validation remain open.

| Area | Current evidence | Remaining work |
| --- | --- | --- |
| Reservation backend | Generation-specific holds, transactional outbox, concurrent idempotency, commit-failure/outage tests and revisioned seat caching | Multi-instance/load validation, operational retry monitoring and retention |
| Customer frontend | Selection, recovery/history/receipts, richer event details and URL-backed discovery filters exercised in a real browser | Deployment validation and larger-catalogue pagination |
| Admin frontend | Venue layouts, draft events, publication and sales closure implemented; dashboard polling works with known placeholder values explicitly unavailable | Accurate health/connections/invalidation/rate measurements and Kafka monitoring |
| Metrics stream | Disconnect/reconnect and multiple subscriptions exercised after lifecycle repair | Broader operational/load validation; frontend currently uses polling |
| Identity and payments | Registered accounts, cookie sessions, CSRF, principal-owned reservations and admin authorization; durable demo payments with success/failure/delay outcomes | Email verification/reset/revocation; gateway sandbox optional |
| Delivery | Images and private-service Compose stack run; HTTP/local HTTPS and database recovery verified | Real-host TLS/credentials, CI, coordinated Redis/Kafka recovery, off-device backups and load evidence |

The original guide's unchecked lists are not a reliable completion checklist. Frontend blocks 1–10 exist and have been stabilized; block 0 still has instrumentation gaps and block 11 needs broader failure and deployment validation. Avoid assigning a completion percentage without first defining whether the target is a portfolio demo or a production ticketing service.

## Changes made

- Typed API responses and consistent errors; stable confirmation payload/idempotency keys across uncertain responses and refresh.
- Persisted tab state and server-returned receipts, partial-hold recovery, conservative expiry handling and corrected route transitions.
- Honest demo payment and operational panels; responsive navigation, mobile seat scrolling, working themes and lazy page loading.
- Explicit loopback CORS origins for local Vite requests. One SSE client now cancels its own subscription instead of shutting down the shared scheduler; application shutdown still stops the scheduler.
- Frontend regression/browser tests and a repeatable real-service validation script with retained, dedicated test records.

## Repository position

At the initial audit, local HEAD was a3a69d2 (Frontend Integration) and fetched origin/master was d944277 (Update README.md), differing in README.md. The current local checkpoint is b384557. The stabilization, recovery and transaction repairs were committed locally as b384557 with an intentionally empty message. Authentication, catalog/pricing, customer wording and payment changes remain uncommitted. Nothing has been pushed to GitHub.

## What to do next

Demo payments, event catalogue, PostgreSQL recovery and deployment packaging are verified locally. Next: automated release checks and validation on the selected hosting environment.

1. Automate build, tests and deployment smoke checks in CI.
2. Select the deployment host/domain, provision credentials and verify public HTTPS and restart behavior there.
3. Address Redis/Kafka retention/recovery and protected off-device backup delivery.
4. Finish measured dashboard values and dependency-outage reporting.
5. Automate isolated checks and package a persistent deployment with restart/restore validation. A provider sandbox remains optional.

See [frontend stabilization and validation](frontend-stabilization.md) for test boundaries and [the ordered roadmap](end-to-end-roadmap.md) for contracts and milestone exit criteria.

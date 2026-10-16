# Reservation transaction hardening

Implemented 16 October 2026. SeatLock now has durable reservation events, generation-specific holds and isolated failure/concurrency tests. It remains a local demo with a supplied user ID and simulated payment; authentication is the next product milestone.

## Commit boundaries

Hold, confirmation, release and expiry write Kafka events to `reservation_outbox` in the same PostgreSQL transaction as the reservation state. Failed commits leave neither a booking nor its outgoing event. Requests do not wait for Kafka. A scheduled worker claims due rows with `FOR UPDATE SKIP LOCKED`, waits for broker acknowledgement and marks delivery. Failures retry with exponential backoff capped at 60 seconds.

Delivery is at least once: a crash after Kafka acknowledgement but before the delivery marker commits can publish again. Every Kafka record carries a stable `outbox-id` header. The simulated booking-notification consumer records booking IDs in a transactional inbox to suppress repeated processing. This does not establish exactly-once external email delivery; a future provider integration needs its own idempotency contract. Observational consumers must tolerate duplicates and workers do not guarantee global event ordering.

Redis release runs only after the database commits. The transaction also persists a cleanup row so an outage or process exit cannot silently discard cleanup. Each hold has a random UUID token, stored with the owner in PostgreSQL and Redis. Atomic compare-and-delete checks the complete owner/token value, so delayed cleanup cannot release a newer hold by the same user.

A hold that rolls back attempts immediate token-specific compensation. If the process dies or cleanup cannot reach Redis, the lock remains bounded by its TTL. Redis outages reject new holds and confirmations; explicit release and expiry can commit their database changes and defer Redis cleanup.

## Concurrency and caching

Confirmation acquires a PostgreSQL transaction advisory lock for its idempotency key before looking up an existing booking. Simultaneous requests with the same key resolve to one booking; existing caller and seat-set checks still reject mismatched reuse. Ordered seat-row locks arbitrate different keys competing for the same allocation.

Reservation mutations advance an event's seat revision in the same transaction. Seat-cache keys include that revision. A loader captures the revision before reading seats and writes only to that namespace, so an old loader cannot overwrite the cache used after a committed mutation. Old versions expire through the existing cache TTL; after-commit legacy-key eviction is best effort. This adds a lightweight PostgreSQL revision query per listing request. Previous performance measurements must be rerun with this design.

## Schema and operations

Flyway V5 adds `seat.hold_token`, `event.seat_revision`, `reservation_outbox` and `consumed_message`. It does not delete existing seats or bookings. Owner-bearing holds from V4 with null tokens retain compatibility until they expire. Stop old application instances before introducing the new token format in a shared deployment; mixed-version rolling operation has not been validated.

The outbox scheduler has a separate worker transaction per row and the scheduling pool has four threads so broker failures do not monopolize expiry scheduling. Delivered outbox rows and inbox records currently remain in the database. Production operation needs retention policy, backlog/oldest-pending alerts and retry visibility; do not delete pending rows to clear an alert.

## Validation

The focused Maven run passed 15 tests: ten isolated hardening scenarios, four reservation-contract tests and one CORS test. The hardening suite owns disposable PostgreSQL, Redis and Kafka containers and covers:

- Hold rollback and actual deferred PostgreSQL constraint failures at hold/booking commit.
- Six simultaneous requests sharing a key and different keys competing for one seat.
- Delayed cleanup after same-user rehold and stale cache-loader writes.
- Physical Redis and Kafka outages followed by recovery and durable delivery.
- Duplicate notification processing and expiry racing confirmation.

Run from the repository root with Docker available and Java 21:

```powershell
.\mvnw.cmd '-Dtest=ReservationHardeningTest,ReservationContractTest,CorsConfigTest' test
```

The real-service recovery suite passed all six scenarios and the full live booking suite passed all ten checks after this milestone, including natural five-minute expiry and Kafka hold/confirmation/release records. The retained live booking event is `f101565c-14ee-463f-a76c-a13c5643dae5`. Reports are under `frontend/test-results/`; execution logs are under `target/hardening-*.log`. The development outbox had no pending Kafka or Redis-release rows after delivery.

Four additional existing regressions passed after updating their expectations for revisioned caching and fail-closed holds: `CacheInvalidationTest`, `CacheStampedeProtectionTest` and `CircuitBreakerIntegrationTest`. The stampede test establishes request completion and cache population under concurrency, not an exact database-query count or a throughput benchmark. Total targeted backend coverage in this pass: 19 passing tests.

This is targeted evidence, not a full-suite, multi-instance or production load certification. A full application crash/restart between broker acknowledgement and outbox marking has not been injected; duplicate tolerance follows the at-least-once design and consumer checks. Real payment, authenticated ownership, server-side pricing, production deployment and operational monitoring remain open.

## Next milestone

Add authentication and derive ownership from the authenticated principal throughout hold, release, confirmation, receipt and history APIs. Remove caller-controlled user IDs from the trust boundary, restrict admin endpoints by role, and test cross-account access and session expiry in both API and browser flows. Then establish server-owned pricing before integrating a payment-provider sandbox.

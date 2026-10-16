# Server-backed reservations — 16 October 2026

> **Latest milestone:** transaction/outbox hardening is implemented and covered by isolated failure/concurrency tests. See [transaction hardening](transaction-hardening.md) for current guarantees and limits. Earlier milestone descriptions below are historical; authenticated identity is next.


The customer frontend now reconstructs active holds and retrieves receipts/history from the backend. Browser session storage is retained for in-progress confirmation retries and convenience, not as the source of booking history.

## Contracts

| Method and path | Result |
| --- | --- |
| POST /api/seats/{seatId}/hold | Existing status/seatId plus event, seat, expiresAt and serverTime |
| GET /api/seats/holds?userId={id} | Active holds with event/seat metadata and server deadlines; only unexpired DB holds with matching live Redis ownership are returned |
| DELETE /api/seats/{seatId}/hold?userId={id} | 204 on release; already available is a harmless retry; another owner's hold or a confirmed seat returns 409 |
| GET /api/bookings/{id}?userId={id} | Receipt containing booking, event and seats; missing or another user's booking returns 404 |
| GET /api/bookings?userId={id}&page=0&size=10 | items, page, size, totalElements and totalPages; stable newest-first ordering, size clamped to 1–50 |

These endpoints still accept the demo userId. Ownership checks are not authentication: a caller can supply someone else's ID. Server-derived identity and authorization remain the next security milestone before public access.

## Customer behavior

- On application startup, check active holds before allowing a new reservation. Restore the current event's holds, or the first active event when there is no matching local event.
- Compute remaining time from expiresAt minus serverTime, conservatively accounting for request transit time. A skewed client clock does not extend the hold.
- After a failed hold response, query active holds rather than blindly retrying the creation request. Successful holds are recovered even when their response was lost.
- Keep an uncertain confirmation's original seat list, user and idempotency key intact. It can still be retried independently of hold recovery.
- Release held seats from selection or checkout. Reconcile the result after partial/uncertain releases. If reconciliation fails, block further reservation actions until recovery succeeds.
- Load receipts and paginated history from the server, including in a fresh browser context. Failed receipt lookup never falls back to displaying a cached successful booking as current truth.
- Explain when a previously saved reservation is no longer active. The customer can check booking history or select seats again.

## Backend changes

Flyway V4 adds nullable held_by and an index for active-owner lookup. No existing bookings or seats are deleted. Existing holds created by the old version have no stored owner: let those expire before upgrading a shared live deployment; they cannot be recovered or confirmed through the new ownership checks.

Hold, confirm and explicit release serialize updates using ordered seat-row locks. Confirmation rejects mixed-event seats, duplicate seat IDs, expired/non-held seats and mismatched ownership. Reusing an idempotency key requires the same user and seat set. Requests are limited to 50 seats and a nonblank key.

Redis ownership/TTL inspection and compare-and-delete are Lua operations. Hold creation now fails closed when Redis is unavailable rather than returning a database-only hold that cannot subsequently be confirmed. The release consumer no longer clears an arbitrary current HELD seat when processing an old event; expiration remains the expiry job's responsibility.

Explicit release invalidates the cache and publishes its event after database commit. This does not solve the project's broader cross-store transaction problem: confirmation/hold/expiry still have pre-commit side effects, commit-failure recovery needs more work, and event delivery needs an outbox. Owner comparison is not a unique generation token for each successive hold. Full generation-aware locking, fault injection and concurrent retry hardening remain follow-up work.

## Validation

- 16 frontend unit tests passed, including server deadline/clock-skew checks.
- Eight controlled browser scenarios passed, including lost hold responses, empty-storage recovery, release, server history, uncertain confirmation retries and blocking reservation actions when recovery is unavailable.
- Five focused Java tests passed: four reservation-contract tests and the existing CORS test. The full backend suite was not run against the shared development database.
- The full live booking regression passed all 10 checks after the changes: real browser checkout, partial conflicts, ownership, throttling, idempotency, PostgreSQL/Redis agreement, natural five-minute expiry and Kafka hold/confirm/release records. Event: efa0b630-b589-4406-b8c8-1d5c0bd20161.
- Six live recovery scenarios passed using real PostgreSQL, Redis, Kafka and a browser: ownership/deadlines/rehold, mixed-event rejection, idempotency ownership/payload checks and pagination, empty-storage recovery/release/rebooking, fresh-context receipt/history, and expired-hold filtering before scheduled cleanup.

The final browser pass also covers a repaired navigation race: the recovery wrapper keeps the payment page mounted while confirmation transitions from pending to completed. Live history assertions identify bookings by ID because durable history correctly includes records from earlier runs.

Run the focused live suite from frontend/ with services on 8080 and 5173:

```powershell
$env:SEATLOCK_LIVE_TEST = '1'
$env:BROWSER_CHANNEL = 'msedge'
npm run test:recovery
```

It creates and retains dedicated test records. It adjusts the deadline of one dedicated test seat to verify expiry filtering; the separate test:live suite waits for natural five-minute expiry. Reports are in frontend/test-results/recovery-validation.json and live-validation.json, ignored by Git.

The next milestone is transaction/failure hardening and authenticated identity, followed by server-side pricing if real payments are planned. Multiple simultaneous active events are returned by the API; the current booking UI works with one event at a time.

# SeatLock: path to an end-to-end project

> **Latest milestone:** transaction/outbox hardening is implemented and covered by isolated failure/concurrency tests. See [transaction hardening](transaction-hardening.md) for current guarantees and limits. Earlier milestone descriptions below are historical; authenticated identity is next.


> **Subsequent milestone completed:** server-backed hold recovery/release, exact expiry and receipt/history retrieval are now implemented. See [reservation recovery](reservation-recovery.md) for the current contracts, checks and remaining boundaries. Earlier sections below record the preceding stabilization pass.

Updated: 16 October 2026. This is an implementation roadmap, not a claim that future stages are complete.

## Current milestone

The backend implements the main reservation services. The frontend now has a buildable booking journey with API response typing, partial-hold recovery, conservative expiry handling, stable confirmation retries, saved server-returned receipts, responsive navigation, and light/dark styling.

Receipts are retained in sessionStorage for the current browser tab. They are not authoritative account history. Payment is explicitly simulated. The admin UI labels backend placeholder measurements as unavailable, and developer events only claim what the browser actually observed.

## 1. Establish a repeatable local booking demo

**Why first:** every later feature depends on a correct hold-to-confirm journey.

Status: the core local demo has now passed ten real-service checks, including browser booking, contention, expiry, database/Redis agreement and Kafka records. See frontend-stabilization.md. Restart and injected-failure coverage still remain.

- Start PostgreSQL, Redis, Kafka and the backend in a disposable development environment.
- Run the frontend checks below, then exercise the same scenarios against real services.
- Use two distinct test users to contend for the same seat. Two tabs sharing the demo identity are not an authentication or ownership test.
- Verify successful multi-seat confirmation, partial holds, 409 conflicts, 429 throttling, expiry, lost responses, refresh and server restart.
- Keep successful holds after a partial failure; the user can continue with that subset. Holds cannot currently be released explicitly by the frontend.

**Exit criteria:** a recorded live run proves that the UI receipt, PostgreSQL booking/seats and Redis hold state agree, including retries and expiry. Kafka delivery should be checked independently.

## 2. Complete the authoritative reservation API

**Why next:** tab-local records cannot reconstruct state on a different device, after a closed tab, or following a database reset.

Proposed endpoints and contracts:

| Contract | Purpose |
| --- | --- |
| Hold response includes server expiresAt and server time | Replace the client TTL estimate and account for clock differences. |
| GET booking by ID, with ownership checks | Load a verified receipt from a URL. |
| GET current user's bookings, paginated | Replace tab-local history with durable history. |
| GET current user's active holds | Recover holds after refresh or uncertain responses. |
| Owner-checked hold release | Allow explicit cancellation and changing seats without waiting for expiry. |
| Server-side price quote | Replace display-only hardcoded prices before taking payments. |

Keep all seats in a booking tied to the same event. Validate availability, hold expiry and ownership at confirmation. Define event sale-time rules and cancellation semantics explicitly.

**Exit criteria:** UI state can be rebuilt from the server; a fabricated booking ID never becomes a successful receipt; cancellation and partial-failure behavior are documented.

## 3. Harden reservation correctness

**Why before payments:** a correct happy path is insufficient when Redis, PostgreSQL or Kafka fail independently.

- Choose consistent Redis-outage behavior. The current database-only hold fallback does not provide complete confirmation continuity.
- Release Redis holds atomically only when the ownership token matches.
- Move cache invalidation to appropriate after-commit handling; test cache repopulation races.
- Persist outgoing events with a transactional outbox, then publish asynchronously with retry and consumer deduplication.
- Bind an idempotency key to the authenticated user and a canonical request payload. Reject key reuse with a different payload; handle simultaneous first requests predictably.
- Test expiry versus confirmation, concurrent confirmations, failed database commits, stale release events and repeated Kafka delivery.
- Review optimistic-lock failures and their HTTP response semantics.

**Exit criteria:** automated failure/concurrency tests establish explicit invariants: one current seat owner, no duplicate confirmed allocation, and eventual event delivery after commit.

## 4. Finish truthful observability

**Why:** the dashboard must describe the system rather than merely display plausible values.

- Completed in this pass: shared SSE scheduler lifecycle repaired and live reconnects verified. Preserve the rule that a client disconnect cancels only its own task.
- Measure cache invalidations, Hikari connections and actual dependency health.
- Compute booking rate over a time window; distinguish cumulative counters from rates and percent changes.
- Add Kafka consumer-lag and delivery/error metrics before enabling those panels.
- Avoid Redis KEYS scans on every dashboard request; use scalable counts/SCAN or sampled instrumentation.
- Add request correlation IDs and structured logs. Correlate API, transaction and event observations without claiming an HTTP success proves Kafka publication.
- Test reconnects and multiple SSE clients, including React StrictMode mounting.

**Exit criteria:** dashboard values agree with service metrics, outage states are visible, and disconnecting one viewer cannot stop updates for another.

## 5. Add identities and authorization

**Why before sharing:** the demo accepts a supplied userId, and the admin endpoints are unauthenticated.

- Add login/session handling and derive user identity on the server.
- Restrict booking/hold operations to their owners; separate customer and admin permissions.
- Protect administrative APIs and exposed operational endpoints.
- Apply rate limits to authenticated principals, and set deployment-specific CORS/session rules.
- Add authorization tests covering another user's booking and direct admin API access.

**Exit criteria:** swapping a userId or booking ID cannot impersonate another customer, and non-admin users cannot read administrative data.

## 6. Decide payment scope deliberately

A polished portfolio demo may keep simulated payment if it is clearly labelled. Real payment is a separate milestone.

If adding it:
- Use a provider's sandbox and hosted/tokenized collection.
- Store payment state and verify signed webhooks server-side.
- Confirm payment success from the provider, not the frontend redirect.
- Make webhook processing idempotent; define late payment, expired holds, failure and refund behavior.
- Reconcile paid amounts with server-side prices.

**Exit criteria:** retries, duplicate webhooks and delayed completion cannot produce duplicate bookings or charges; failed/expired bookings have a defined recovery path.

## 7. Make validation reproducible and automatic

- Remove local-service assumptions from backend Testcontainers tests; in particular use the Kafka container's actual bootstrap address.
- Correct Gatling's missing holdStatus capture and userId session value.
- Use realistic test identities and disclose rate-limit settings.
- Report successful holds, expected conflicts, throttles, actual confirmations and infrastructure errors separately.
- Add GitHub Actions jobs for frontend build/lint/tests, browser scenarios, backend tests and migration checks.
- Publish test environment, commit SHA, dataset size, load profile and raw reports.

**Exit criteria:** a clean checkout can run the suite; reported capacity includes completed bookings and correctness checks, not only accepted HTTP statuses.

## 8. Package and deploy a bounded demo

- Build backend/frontend images and define persistent database storage, health checks, startup behavior and environment configuration.
- Serve the frontend and /api through a reverse proxy with SPA route fallback.
- Configure Kafka advertised addresses for the deployment network.
- Use HTTPS, external secrets, restricted operational endpoints, backups and a tested restore procedure.
- Start with one reproducible deployment. Add multiple application instances when validating distributed contention and failover; Kubernetes is optional.
- Document startup, migration, reset, rollback and troubleshooting commands.

**Exit criteria:** another developer can start the project from the README, and a deployed demo survives restart without silently losing bookings.

## Frontend validation

From frontend/:

```powershell
npm ci
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

The test script uses Node's native TypeScript support; use Node 24. Browser tests start and stop their own Vite server on port 4179 and use controlled API fixtures. They do not access the database or establish real backend correctness.

To use an installed Edge browser on Windows instead of downloading Chromium:

```powershell
$env:BROWSER_CHANNEL = 'msedge'
npm run test:e2e
```

Screenshots are written to frontend/test-results/ (ignored by Git).

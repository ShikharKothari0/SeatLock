# SeatLock frontend

React/TypeScript customer booking demo and administrative dashboard.

## Run

Use Node 24 and npm. Start the Spring Boot backend on localhost:8080 with its PostgreSQL, Redis and Kafka services, then run:

```powershell
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies /api to the backend. Payment is simulated; the application uses a seeded demo user.

## Validate

```powershell
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

The browser suite starts its own Vite server on port 4179 and intercepts API requests with controlled fixtures. It checks frontend behavior, not live backend correctness. To use an installed Edge browser on Windows:

```powershell
$env:BROWSER_CHANNEL = 'msedge'
npm run test:e2e
```

Browser screenshots are saved under test-results/ and ignored by Git.

### Live integration validation

With Docker Compose, the backend on 8080 and Vite on 127.0.0.1:5173 running, execute from frontend/:

```powershell
$env:SEATLOCK_LIVE_TEST = '1'
$env:BROWSER_CHANNEL = 'msedge'
npm run test:live
```

This uses real services and takes about six minutes, including the natural five-minute hold expiry. It creates and retains a dedicated event, venue, four users and twelve seats; it does not clear existing data. It requires the Compose PostgreSQL/Redis/Kafka services and development database credentials. Reports are saved to test-results/live-validation.json and an event-specific JSON file. Checks cover browser booking and partial conflicts, ownership, idempotency, throttling, expiry, database/Redis agreement, Kafka records and metrics-stream reconnects. This is local demo validation, not a substitute for isolated backend failure tests.

## State and limitations

- Active holds are recovered from the server on startup, including after browser storage is cleared.
- Receipts and paginated history are loaded from the server for the seeded demo user.
- Hold responses include server expiry/time; the countdown accounts for clock skew conservatively.
- Explicit release is available from selection and checkout; uncertain releases are reconciled before continuing.
- A pending confirmation retains its original request and idempotency key across retries and refresh.
- Failed hold responses trigger recovery rather than automatic hold retries.
- The application still uses a supplied demo identity. Authentication and actual payment processing remain future milestones.
- Transactional outbox delivery, generation-aware holds and isolated failure tests are implemented; see [transaction hardening](../docs/transaction-hardening.md). Admin placeholder metrics remain labelled unavailable.

Run npm run test:recovery with the same live-test environment variables above for the new real-service recovery checks. It retains dedicated test data and adjusts one test seat's deadline; test:live separately verifies natural expiry.

See [reservation recovery](../docs/reservation-recovery.md) for contracts and validation, and [the roadmap](../docs/end-to-end-roadmap.md) for later milestones.

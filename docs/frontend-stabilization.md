# Frontend stabilization — 16 October 2026

> **Subsequent milestone completed:** server-backed hold recovery/release, exact expiry and receipt/history retrieval are now implemented. See [reservation recovery](reservation-recovery.md) for the current contracts, checks and remaining boundaries. Earlier sections below record the preceding stabilization pass.

## Implemented changes by integration-guide block

| Block | Changes in this pass | Remaining boundary |
| --- | --- | --- |
| 0 — Backend prerequisites | Frontend hides known placeholder health/connection/invalidation values rather than treating them as measurements. | Backend instrumentation remains incomplete. The SSE scheduler lifecycle was repaired and live reconnects verified. |
| 1–2 — Scaffold, types, API | Correctly typed response-body extraction, normalized 4xx/network/5xx errors, working ESM imports, Node regression tests and browser test harness. | Live browser booking now verified; authoritative recovery APIs remain missing. |
| 3 — Session stores | Tab-scoped persistence, full booking receipts, earliest hold deadline, event isolation, stable pending confirmation payload/key. | sessionStorage is not authoritative server history; up to 50 receipts are retained per tab. |
| 4 — Hooks | Non-idempotent hold requests are not auto-retried; successful changes invalidate queries; confirmation retries reuse the same request and key, including after reload. | Lost hold responses require an active-holds API to reconcile reliably. |
| 5–6 — Shared components and seat selection | Functional theme styles, mobile layouts, accessible seat names, one functioning hold action, partial-success recovery and countdown based on elapsed wall time. | Hold expiry is estimated conservatively from request start until the API supplies expiry. |
| 7 — Developer console | Actual client-observed HTTP latency and correct Kafka topic names; Kafka delivery explicitly unverified. | Redis/DB/cache widgets have no invented observations; correlated backend traces remain future work. |
| 8 — Customer pages | Checkout uses held seats only; demo payment collects no real card information; verified API receipts survive reload; unknown IDs are not displayed as confirmed; history uses saved receipts. | Authentication, server-backed receipt/history retrieval, explicit hold release and real payments are not implemented. |
| 9 — Admin pages | Removed invented trend percentages; cumulative counts labelled; missing metrics labelled unavailable; mobile navigation and unknown circuit-breaker rates handled. | Kafka dashboard is still a planned extension. |
| 10 — Routing | Empty-checkout guards, pending-confirmation recovery, unknown route handling and lazy-loaded pages. | Production SPA fallback must be configured when deploying. |
| 11 — Validation | Build/lint, 13 API/session regression tests and six browser scenarios passed. Dark/light desktop and mobile screenshots inspected. | Live multi-user booking checks added; broader outage, transaction and deployment tests remain necessary. |

## Verified in this pass

- `npm run build`: passed after the source fixes.
- `npm run lint`: exited successfully.
- `npm test`: 13 tests passed.
- `npm run test:e2e`: six scenarios passed in headless Microsoft Edge using controlled API responses.
- Screenshots: light/dark seat selection and mobile seat selection/admin health.

Browser scenarios cover multi-seat checkout and receipt/history reload, unknown booking IDs, partial holds, 429 and 500 handling, lost confirmation response with stable retries after expiry/reload, expired hold recovery, themes, route guards and admin navigation.

Docker Desktop and all six Compose services are now running, together with the backend and Vite. The initial daemon limitation has been resolved. See the live validation report below for current evidence. The full backend integration suite was not rerun: some existing tests reset shared database records and still assume local Kafka.

## Live validation — 16 October 2026

Result: **PASS**, 10 checks passed against real PostgreSQL, Redis, Kafka, Spring Boot and Vite, using headless Microsoft Edge without API mocks.

- backend and real frontend proxy respond.
- metrics stream survives disconnect and reconnect.
- five-minute expiry hold created: {"seatId":"f385ce29-2450-4212-9149-30206225b46d","initialTtlSeconds":299}.
- two distinct users contend: exactly one hold succeeds: {"statuses":[200,409],"wrongOwnerStatus":409,"bookingId":"d13ad5fd-cb6c-494f-908f-d8ee673ed5e3","repeatedBookingId":"d13ad5fd-cb6c-494f-908f-d8ee673ed5e3"}.
- sixth hold in a ten-second user window is throttled: [200,200,200,200,200,429].
- real browser multi-seat booking, receipt refresh and history: {"bookingId":"a208c93f-dc20-447f-af6e-554b7397ec5b","seatIds":["b5a59d56-83d0-4299-9edd-6e0c2afd21a9","423c10b9-01ee-43ff-b23d-4094347723b1"]}.
- browser handles a real concurrent-seat conflict without losing its first hold: {"bookingId":"62338565-33ed-43aa-b15d-825dd15abfbc","seatIds":["6ace353b-1859-4dc0-9fe8-6f52dd76ea53"]}.
- PostgreSQL contains the expected confirmed bookings and seats: {"confirmedBookings":3,"confirmedSeats":4}.
- real five-minute hold expires in Redis and PostgreSQL; confirmation is rejected: {"elapsedSeconds":326,"confirmStatus":409}.
- Kafka contains hold, confirmation and release events for this run: {"seat-held":11,"seat-confirmed":3,"seat-released":7}.

Dedicated event: e106c299-0de8-4d7e-8888-96919afda99d. Three confirmed bookings cover four seats. Test records are retained; existing records were not cleared. Raw reports and the receipt screenshot are in frontend/test-results/ (ignored by Git). The repeatable command is documented in frontend/README.md.

The first live attempt exposed a loopback-origin CORS rejection. A separate probe reproduced the shared SSE scheduler shutting down after one client disconnected. Both were repaired; the final run above includes real browser POST requests and SSE reconnect/multiple-client coverage. The focused CorsConfigTest also passed (one test).

This run does not establish correctness under database commit failure, Kafka/Redis outages, stale event replay, simultaneous confirmation requests, application restart during a booking, or multi-instance load. Lost-response recovery is covered by controlled frontend browser scenarios, not by network fault injection in this live run.

## Important semantics

A pending confirmation is retained after transport/5xx failures because the server might already have committed the booking. A retry reuses the original key and seats, even if the local timer has elapsed. A successful response saves the actual server booking; a business rejection ends the pending state.

The timer uses the earliest request-start-plus-five-minutes deadline across held seats. This avoids extending earlier holds when later seats succeed. It is still an estimate, not server authority.

The tab receipt is a snapshot of a server response, not proof that the booking still exists after a server reset or later cancellation.

## Next work

Follow [the ordered end-to-end roadmap](end-to-end-roadmap.md). The immediate next milestone is a repeatable live demo plus authoritative booking/hold retrieval APIs, followed by transaction/failure hardening and truthful operational metrics.

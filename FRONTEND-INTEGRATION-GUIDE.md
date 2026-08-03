# SeatLock — Frontend Integration Guide

> This document specifies the existing backend structure relevant to frontend
> development, the API contracts the frontend must conform to, known constraints
> and business rules, the backend additions that must be built before frontend
> work begins, and the ordered task list for building the complete frontend.
>
> Intended audience: anyone building the SeatLock frontend who should not need
> to read the Spring Boot source code to understand what the backend does.

\---

## Part 1 — Existing Backend API Surface

The backend currently exposes these REST endpoints. All return JSON. All are
accessible at `http://localhost:8080`. CORS is not yet configured — this is a
backend prerequisite task (see Part 3).

\---

### 1.1 Events API

#### `GET /api/events`

Returns all events in the system.

**Response:** array of `EventResponse`

```json
\[
  {
    "id": "22222222-2222-2222-2222-222222222222",
    "name": "Test Concert",
    "venueName": "Test Arena",
    "venueCity": "Mumbai",
    "saleStartTime": "2026-07-29T13:08:02Z",
    "eventTime": "2026-08-05T13:08:02Z"
  }
]
```

#### `GET /api/events/{id}`

Returns a single event by UUID. Returns `404` with `{ "message": "Event not found: {id}", "timestamp": "..." }` if the event does not exist.

**Response:** single `EventResponse` (same shape as above, not wrapped in array)

\---

### 1.2 Seats API

#### `GET /api/events/{eventId}/seats`

Returns all seats for an event. Accepts an optional `?status=` query parameter
to filter by seat status.

**Query parameters:**

|Parameter|Type|Required|Values|
|-|-|-|-|
|`status`|string|No|`AVAILABLE`, `HELD`, `CONFIRMED`|

**Response:** array of `SeatResponse`

```json
[
  {
    "id": "00000001-0000-0000-0000-000000000000",
    "seatNumber": "A1",
    "section": "Section A",
    "status": "AVAILABLE"
  },
  {
    "id": "00000002-0000-0000-0000-000000000000",
    "seatNumber": "A2",
    "section": "Section A",
    "status": "HELD"
  }
]
```

**Caching note:** this endpoint is cache-backed in Redis with a 45-second TTL.
Concurrent reads hit the cache, not Postgres. After any hold or confirm
operation, the cache is invalidated — the next read will be a cache miss and
will query Postgres for fresh data. The frontend should poll this endpoint
every 5 seconds to show near-real-time seat availability.

\---

### 1.3 Seat Hold API

#### `POST /api/seats/{seatId}/hold`

Attempts to acquire a distributed Redis lock on a seat and mark it as `HELD`
in Postgres. The hold lasts 5 minutes (300 seconds). After 5 minutes without
confirmation, the expiry job releases the seat back to `AVAILABLE`.

**Path parameters:** `seatId` — UUID of the seat to hold

**Request body:**

```json
{
  "userId": "33333333-3333-3333-3333-333333333333"
}
```

**Success response — 200 OK:**

```json
{
  "status": "HELD",
  "seatId": "00000001-0000-0000-0000-000000000000"
}
```

**Failure responses:**

|Status|Condition|Body|
|-|-|-|
|`409 Conflict`|Seat is already held or not available|`{ "message": "Seat ... is already held", "timestamp": "..." }`|
|`429 Too Many Requests`|User has exceeded 5 hold attempts in 10 seconds|`{ "message": "Too many hold requests — please wait before trying again", "timestamp": "..." }`|
|`404 Not Found`|Seat UUID does not exist|`{ "message": "Seat not found: ...", "timestamp": "..." }`|
|`400 Bad Request`|Missing or invalid `userId` in body|`{ "message": "userId: userId is required", "timestamp": "..." }`|

**Important frontend constraint:** the rate limiter allows **5 hold requests
per user per 10-second window**. If the user tries to hold a 6th seat within
10 seconds, the endpoint returns `429`. The frontend should display a friendly
message and not retry automatically.

**What this triggers internally (for Developer Console display):**

1. Rate limit token consumed from Redis bucket (`ratelimit:hold:{userId}`)
2. Redis Lua script executes atomically: sets `seat:lock:{seatId}` with 300s TTL
3. Postgres: seat status updated to `HELD`, `hold\_expires\_at` set to now + 5min
4. Redis cache for event seats is evicted (`seats:event:{eventId}` deleted)
5. Kafka event `SeatHeldEvent` published to `seat-held` topic

\---

### 1.4 Booking API

#### `POST /api/bookings/confirm`

Validates that the requesting user holds the Redis locks for all specified
seats, then writes a confirmed booking to Postgres, flips seat statuses to
`CONFIRMED`, releases Redis locks, and publishes a Kafka event.

**Request body:**

```json
{
  "userId": "33333333-3333-3333-3333-333333333333",
  "seatIds": \["00000001-0000-0000-0000-000000000000"],
  "idempotencyKey": "unique-client-generated-string"
}
```

**Field notes:**

* `seatIds` — array of one or more UUIDs. All must be held by the same user.
* `idempotencyKey` — client-generated unique string (UUID recommended). If the
same key is submitted again, the server returns the original booking response
without creating a duplicate. This makes the confirm endpoint safe to retry
after a network timeout.

**Success response — 200 OK:**

```json
{
  "id": "booking-uuid-here",
  "userId": "33333333-3333-3333-3333-333333333333",
  "eventId": "22222222-2222-2222-2222-222222222222",
  "status": "CONFIRMED",
  "createdAt": "2026-07-29T13:08:02Z",
  "seatIds": \["00000001-0000-0000-0000-000000000000"]
}
```

**Failure responses:**

|Status|Condition|Body|
|-|-|-|
|`409 Conflict`|Hold expired or belongs to different user|`{ "message": "Hold has expired or does not exist for seat: ...", "timestamp": "..." }`|
|`409 Conflict`|Duplicate idempotency key race condition|`{ "message": "A booking with this idempotency key already exists...", "timestamp": "..." }`|
|`404 Not Found`|User or seat not found|`{ "message": "User not found: ...", "timestamp": "..." }`|
|`400 Bad Request`|Validation failure on any field|`{ "message": "seatIds: ...", "timestamp": "..." }`|

**Idempotent retry behavior:** if the same `idempotencyKey` is submitted twice
(e.g., user tapped "Confirm" twice, or a network retry fired), the second
response is identical to the first — same booking ID, same seat IDs, same
status. No duplicate booking is created. The frontend can rely on this: always
generate one idempotency key per checkout attempt and reuse it on retries.

\---

### 1.5 Error envelope

All error responses follow this consistent shape:

```json
{
  "message": "Human-readable description of the error",
  "timestamp": "2026-07-29T13:08:02.123456789Z"
}
```

The frontend should display `message` directly to the user for 4xx errors.
For `5xx` errors, display a generic "Something went wrong" message instead
of the server's message (which may contain internal details).

\---

## Part 2 — Data Models and Enums

### 2.1 TypeScript types (mirror these exactly)

```typescript
// All UUID fields are strings in ISO 8601 UUID format.
// All timestamps are ISO 8601 strings in UTC.

type SeatStatus = 'AVAILABLE' | 'HELD' | 'CONFIRMED'
type BookingStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED'

interface EventResponse {
  id: string
  name: string
  venueName: string
  venueCity: string
  saleStartTime: string   // ISO 8601
  eventTime: string       // ISO 8601
}

interface SeatResponse {
  id: string
  seatNumber: string      // e.g. "A1", "A2", ... "A100"
  section: string         // e.g. "Section A"
  status: SeatStatus
}

interface HoldResponse {
  status: 'HELD'
  seatId: string
}

interface BookingResponse {
  id: string
  userId: string
  eventId: string
  status: BookingStatus
  createdAt: string       // ISO 8601
  seatIds: string\[]
}

interface ApiError {
  message: string
  timestamp: string
}

// Request bodies
interface HoldRequest {
  userId: string
}

interface BookingConfirmRequest {
  userId: string
  seatIds: string\[]
  idempotencyKey: string
}
```

### 2.2 Seat number format

The seed data generates seat numbers as `A1` through `A100`. They are stored
as the string `'A' + number`. The seat grid component should display them in a
10×10 grid layout (rows A–J visually, though all are labelled `A1`–`A100` in
the seed data).

For a more realistic visual layout, render the 100 seats in 10 rows of 10,
labelling rows with letters (A–J) and columns with numbers (1–10), derived
from the seat index, regardless of the raw `seatNumber` string from the API.

\---

## Part 3 — Seed Data (Hardcoded for Development)

All seed data is deterministic — the same UUIDs every time the database is
reset. The frontend can hardcode these for the demo flow.

|Entity|UUID|Detail|
|-|-|-|
|Venue|`11111111-1111-1111-1111-111111111111`|Test Arena, Mumbai|
|Event|`22222222-2222-2222-2222-222222222222`|Test Concert|
|User|`33333333-3333-3333-3333-333333333333`|testuser@seatlock.com|
|Seats A1–A100|Sequential deterministic UUIDs|`00000001-0000-0000-0000-000000000000` through `00000100-0000-0000-0000-000000000000`<br /><br />Seats 1 to 10 belong to section A<br />Seats 11 to 20 belong to section B and so on|

The `userId` (`33333333-...`) is used for all hold and confirm requests in the
demo. The frontend does not implement authentication — the session store simply
holds this hardcoded UUID as the "logged in" user.

\---

## Part 4 — Backend Additions Required Before Frontend Work

The following must be built in the backend before any frontend page can call
the API successfully. These are new files — nothing existing needs to change.

\---

### 4.1 CORS Configuration

**File:** `src/main/java/com/ShikharKothari0/SeatLock/config/CorsConfig.java`

Without this, every frontend request gets blocked by the browser's same-origin
policy. The Vite dev server runs on port `5173`; Spring Boot runs on `8080`.

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/\*\*")
            .allowedOrigins(
                "http://localhost:5173",
                "http://localhost:3000"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("\*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

**Verification:** after adding, restart the app and run:

```
GET http://localhost:8080/api/events
```

from a browser tab at `http://localhost:5173`. If the request succeeds instead
of showing a CORS error in the browser console, CORS is working.

\---

### 4.2 Admin DTO Records

**Package:** `src/main/java/com/ShikharKothari0/SeatLock/dto/admin/`

Create one `.java` file per record:

**`MetricsOverviewResponse.java`**

```java
public record MetricsOverviewResponse(
    long totalBookings,
    double bookingsPerMinute,
    long totalHolds,
    long holdsExpired,
    long holdsRejected,
    double holdSuccessRate,
    double avgHoldLatencyMs,
    double p99HoldLatencyMs
) {}
```

**`CacheMetricsResponse.java`**

```java
public record CacheMetricsResponse(
    long cacheHits,
    long cacheMisses,
    double hitRatio,
    long cacheInvalidations,
    long activeCacheKeys
) {}
```

**`RedisMetricsResponse.java`**

```java
public record RedisMetricsResponse(
    boolean connected,
    long activeLocks,
    long memoryUsedBytes,
    long totalKeys,
    double hitRatio,
    long connectedClients
) {}
```

**`KafkaMetricsResponse.java`**

```java
public record KafkaMetricsResponse(
    long messagesPublished,
    long messagesConsumed,
    long dlqMessages,
    Map<String, Long> consumerLagByTopic
) {}
```

**`CircuitBreakerMetricsResponse.java`**

```java
public record CircuitBreakerMetricsResponse(
    String name,
    String state,
    double failureRate,
    long failedCalls,
    long successfulCalls,
    long bufferedCalls
) {}
```

**`SystemHealthResponse.java`**

```java
public record SystemHealthResponse(
    String status,
    double cpuUsage,
    long heapUsedBytes,
    long heapMaxBytes,
    int liveThreads,
    int hikariActiveConnections,
    int hikariPendingConnections,
    List<CircuitBreakerMetricsResponse> circuitBreakers
) {}
```

**`MetricStreamSnapshot.java`**

```java
public record MetricStreamSnapshot(
    MetricsOverviewResponse overview,
    CacheMetricsResponse cache,
    RedisMetricsResponse redis,
    List<CircuitBreakerMetricsResponse> circuitBreakers,
    Instant timestamp
) {}
```

\---

### 4.3 AdminMetricsService

**File:** `src/main/java/com/ShikharKothari0/SeatLock/service/AdminMetricsService.java`

Aggregates Micrometer counters, Redis INFO, and Resilience4j registry data into
the DTO shapes above. Full implementation is in `FRONTEND-ARCHITECTURE.md`
Section 5c. Key methods:

* `getOverview()` → reads `seatlock.holds.created`, `seatlock.holds.rejected`,
`seatlock.bookings.confirmed`, `seatlock.holds.expired` counters and the
`seatlock.holds.latency` timer
* `getCacheMetrics()` → reads `seatlock.cache.hits`, `seatlock.cache.misses`,
counts `seats:event:\*` keys in Redis
* `getRedisMetrics()` → calls `redisTemplate.getConnectionFactory() .getConnection().serverCommands().info()` to get Redis server stats
* `getCircuitBreakerMetrics()` → iterates `circuitBreakerRegistry .getAllCircuitBreakers()`

\---

### 4.4 AdminController

**File:** `src/main/java/com/ShikharKothari0/SeatLock/controller/AdminController.java`

Exposes all admin metrics as REST endpoints plus one SSE stream endpoint.
Full implementation is in `FRONTEND-ARCHITECTURE.md` Section 5d.

**Endpoints exposed:**

|Method|Path|Returns|
|-|-|-|
|`GET`|`/api/admin/metrics/overview`|`MetricsOverviewResponse`|
|`GET`|`/api/admin/metrics/cache`|`CacheMetricsResponse`|
|`GET`|`/api/admin/metrics/redis`|`RedisMetricsResponse`|
|`GET`|`/api/admin/metrics/circuit-breakers`|`List<CircuitBreakerMetricsResponse>`|
|`GET`|`/api/admin/health`|`SystemHealthResponse`|
|`GET`|`/api/admin/metrics/stream`|SSE stream of `MetricStreamSnapshot` every 3s|

**Testing the SSE stream** before writing frontend code:

```bash
curl -N http://localhost:8080/api/admin/metrics/stream
```

You should see JSON events printed every 3 seconds. If nothing appears, the
`AdminController` is not registered or the `AdminMetricsService` is failing.

\---

### 4.5 `application.properties` addition

```properties
# Allow frontend to reach the Prometheus endpoint indirectly via AdminController
# (the frontend does NOT call Actuator directly — only via /api/admin/\*)
seatlock.frontend.url=http://localhost:5173

# Needed for the admin metrics SSE to work correctly under load
spring.mvc.async.request-timeout=300000
```

\---

## Part 5 — Business Rules the Frontend Must Enforce

These are constraints that exist in the backend but also need to be reflected
in the frontend UX to avoid confusing error states.

### 5.1 Hold TTL — 5 minutes

Once a seat is held (`POST /api/seats/{seatId}/hold` returns 200), the user
has exactly 5 minutes to complete the booking. After that:

* Redis TTL expires, the lock key is deleted
* The `HoldExpiryService` (runs every 30 seconds) detects the expired hold
and flips the seat back to `AVAILABLE`
* Any subsequent `POST /api/bookings/confirm` with those seat IDs will get a
`409` with "Hold has expired"

**Frontend requirement:** display a countdown timer starting from 5:00 when
the hold is acquired. On expiry, show a modal and redirect the user back to
the seat selection page. Do not allow the user to proceed to confirm after
the timer expires.

The hold expiry timestamp is not currently returned by the hold endpoint — it
returns only `{ status: "HELD", seatId: "..." }`. The frontend should
calculate expiry as `Date.now() + 300\_000` (5 minutes in ms) at the moment
the 200 response is received.

### 5.2 Rate limiting — 5 holds per 10 seconds per user

The hold endpoint rate-limits by `userId`. After 5 hold requests within a
10-second window, the 6th request returns `429`. The window resets after
10 seconds.

**Frontend requirement:** on `429`, show a user-friendly message like "You're
trying too quickly — please wait a moment." Do not retry automatically. The
window resets in at most 10 seconds, after which the user can try again.

### 5.3 Multi-seat booking

The `POST /api/bookings/confirm` endpoint accepts an array of `seatIds`. All
seats in the array must be held by the same `userId`. The frontend can allow
users to select multiple seats before holding them, but must hold each seat
individually (one `POST /api/seats/{seatId}/hold` per seat). There is no
bulk-hold endpoint.

**Frontend requirement:** hold all selected seats before navigating to checkout.
If any hold fails (409 or 429), stop the hold sequence and show an error.

### 5.4 Idempotency key generation

The `idempotencyKey` in the confirm request must be unique per checkout
attempt. Generate it client-side using `crypto.randomUUID()` when the user
first enters the checkout flow. Store it in the session store. Reuse the same
key on any retry of the same confirm request.

```typescript
const idempotencyKey = crypto.randomUUID()
```

### 5.5 The confirm endpoint validates Redis holds

If the user closes the browser after holding seats, navigates away, or waits
for the 5-minute TTL to expire, the Redis hold keys are gone. Any confirm
attempt returns `409`. The frontend must handle this gracefully — redirect back
to the event page with a message explaining the hold expired.

\---

## Part 6 — Frontend Task List

Ordered by dependency. Each task block assumes the previous block is complete.

\---

### Block 0 — Backend prerequisites (complete before writing frontend code)

* \[ ] Create `CorsConfig.java` (Part 4.1) and verify CORS works from browser
* \[ ] Create all admin DTO records in `dto/admin/` package (Part 4.2)
* \[ ] Create `AdminMetricsService.java` (Part 4.3)
* \[ ] Create `AdminController.java` (Part 4.4)
* \[ ] Add `spring.mvc.async.request-timeout=300000` to `application.properties`
* \[ ] Test all five admin endpoints return valid JSON via Postman
* \[ ] Test SSE stream via `curl -N http://localhost:8080/api/admin/metrics/stream`
* \[ ] Confirm existing endpoints work after CORS addition via browser console

\---

### Block 1 — Project scaffold

* \[ ] Create `frontend/` directory at project root
* \[ ] Run `npm create vite@latest . -- --template react-ts` inside `frontend/`
* \[ ] Install all dependencies:

```bash
  npm install react-router-dom @tanstack/react-query axios zustand framer-motion recharts lucide-react
  npm install -D tailwindcss autoprefixer postcss
  npx tailwindcss init -p
  ```

* \[ ] Configure `tailwind.config.ts` with design tokens from
`FRONTEND-ARCHITECTURE.md` Section 4 (color palette, font families)
* \[ ] Add Google Fonts to `index.html`:

```html
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700\&family=JetBrains+Mono:wght@400;500;700\&display=swap" rel="stylesheet">
  ```

* \[ ] Add Tailwind directives to `src/index.css`
* \[ ] Configure `vite.config.ts` with proxy:

```typescript
  server: { proxy: { '/api': 'http://localhost:8080' } }
  ```

* \[ ] Verify proxy works: start Vite dev server, confirm
`http://localhost:5173/api/events` returns event data

\---

### Block 2 — TypeScript types and utilities

* \[ ] Create `src/types/api.ts` with all interfaces from Part 2.1
* \[ ] Create `src/types/metrics.ts` mirroring all admin DTO shapes
* \[ ] Create `src/lib/api.ts` — Axios instance with `baseURL: '/api'` and
response interceptor that extracts `data` and handles errors
* \[ ] Create `src/lib/formatters.ts`:

  * `formatMs(ms: number): string` — formats `152` as `"152ms"`, `1540` as `"1.5s"`
  * `formatBytes(bytes: number): string` — formats `134217728` as `"128 MB"`
  * `formatCount(n: number): string` — formats `12842` as `"12.8K"`
  * `formatPercent(n: number): string` — formats `92.14` as `"92.1%"`
* \[ ] Create `src/lib/constants.ts`:

  * `EVENT\_ID = '22222222-2222-2222-2222-222222222222'`
  * `USER\_ID = '33333333-3333-3333-3333-333333333333'`
  * `HOLD\_DURATION\_MS = 300\_000`
  * Status badge color map: `ACQUIRED → status-success`, etc.

\---

### Block 3 — Zustand stores

* \[ ] Create `src/store/themeStore.ts`:

  * State: `theme: 'dark' | 'light'`
  * Actions: `toggle()`
  * Persistence: `localStorage` via Zustand middleware
  * Default: `'dark'`
* \[ ] Create `src/store/sessionStore.ts`:

  * State: `userId`, `selectedSeatIds`, `heldSeatIds`, `holdExpiresAt`, `idempotencyKey`, `lastBookingId`
  * Actions: `selectSeat()`, `deselectSeat()`, `clearSelection()`,
`setHeld()`, `clearHold()`, `setBookingConfirmed()`
* \[ ] Create `src/store/developerModeStore.ts`:

  * State: `enabled`, `events: DevEvent\[]` (last 50, most recent first)
  * Actions: `toggle()`, `emitEvent(event: DevEvent)`, `clearEvents()`
  * The `DevEvent` interface is specified in `FRONTEND-ARCHITECTURE.md` Section 8

\---

### Block 4 — Custom hooks

* \[ ] Create `src/hooks/useTheme.ts` — applies `dark` class to
`document.documentElement` when theme is `'dark'`, removes it otherwise.
Runs on mount and whenever theme changes.
* \[ ] Create `src/hooks/useMetricStream.ts` — wraps native `EventSource`:

  * Opens `EventSource('/api/admin/metrics/stream')`
  * Listens for `'metrics'` events, parses JSON, stores latest snapshot in state
  * Returns `{ snapshot: MetricStreamSnapshot | null, connected: boolean }`
  * Auto-reconnects on error (EventSource does this natively)
  * Closes `EventSource` on component unmount
* \[ ] Create `src/hooks/useMetrics.ts` — TanStack Query wrappers:

  * `useOverviewMetrics()` — polls `/api/admin/metrics/overview` every 5s
  * `useCacheMetrics()` — polls `/api/admin/metrics/cache` every 5s
  * `useRedisMetrics()` — polls `/api/admin/metrics/redis` every 5s
  * `useCircuitBreakers()` — polls `/api/admin/metrics/circuit-breakers` every 5s
* \[ ] Create `src/hooks/useSeatHold.ts` — TanStack Mutation wrapper:

  * Calls `POST /api/seats/{seatId}/hold`
  * On success: calls `sessionStore.setHeld()`, calls
`developerModeStore.emitEvent()` with a `DevEvent` of type `'SEAT\_HOLD'`
  * On error: does NOT emit a dev event, returns the error for the UI to handle
* \[ ] Create `src/hooks/useBookingConfirm.ts` — TanStack Mutation wrapper:

  * Calls `POST /api/bookings/confirm`
  * On success: calls `sessionStore.setBookingConfirmed()`, emits
`DevEvent` of type `'BOOKING\_CONFIRM'`
  * Reads `idempotencyKey` from `sessionStore` (generated once at checkout entry)

\---

### Block 5 — Common components

* \[ ] Create `src/components/common/StatusBadge.tsx`:

  * Props: `status: string` (any status string from the system)
  * Maps status strings to colors and display text:

    * `ACQUIRED`, `CONFIRMED`, `CONNECTED`, `CLOSED`, `SUCCESS` → green, `status-success`
    * `PENDING`, `WAITING`, `HELD`, `HALF\_OPEN` → amber, `status-pending`
    * `FAILED`, `OPEN`, `DISCONNECTED`, `REJECTED` → red, `status-failure`
    * `MISS`, `PUBLISHED`, `INFO` → blue, `status-info`
  * Style: `font-mono text-xs uppercase tracking-widest px-2 py-0.5 rounded`
  * Active statuses (ACQUIRED, CONNECTED) should have a subtle pulse animation
* \[ ] Create `src/components/common/MetricCard.tsx`:

  * Props: `label: string`, `value: string | number`, `unit?: string`,
`trend?: number` (positive = good, negative = bad, undefined = no trend)
  * Large mono value, small label below, optional trend chip in corner
  * Dark card with surface-raised background, surface-border border
* \[ ] Create `src/components/common/ThemeToggle.tsx`:

  * Sun icon (light mode) / Moon icon (dark mode) from `lucide-react`
  * Calls `themeStore.toggle()` on click
* \[ ] Create `src/components/common/LoadingSpinner.tsx`:

  * Spinning ring in brand-primary color
  * Sizes: `sm`, `md`, `lg`
* \[ ] Create `src/components/common/Navbar.tsx`:

  * Left: SeatLock logo (text-based: "Seat**Lock**" with bold Lock)
  * Right: ThemeToggle + `DEV` badge that toggles developer mode
  * The `DEV` badge: small pill, green when developer mode is on, gray when off
  * Sticky, `surface-raised` background with bottom border

\---

### Block 6 — Customer Portal: seat grid

This is the most important customer component. Build and test it in isolation
before wiring into a full page.

* \[ ] Create `src/components/customer/SeatCell.tsx`:

  * Props: `seat: SeatResponse`, `isSelected: boolean`, `onToggle: () => void`
  * Visual states:

    * `AVAILABLE` + not selected: green filled circle, cursor pointer
    * `AVAILABLE` + selected: violet ring + fill, cursor pointer
    * `HELD`: amber filled circle at 60% opacity, cursor not-allowed, tooltip
"Held by another user"
    * `CONFIRMED`: gray filled circle, cursor not-allowed
  * Size: 32×32px, rounded-full
  * Status changes should animate with a 150ms color transition
* \[ ] Create `src/components/customer/SeatGrid.tsx`:

  * Props: `eventId: string`, `selectedSeatIds: string\[]`,
`onSeatToggle: (seatId: string) => void`
  * Uses TanStack Query to fetch `/api/events/{eventId}/seats` with
`refetchInterval: 5000`
  * Renders a stage indicator at the top ("STAGE" label spanning full width)
  * Renders seats in a 10-column grid layout, labelled with row letters A–J
derived from index (seat 0–9 = row A, 10–19 = row B, etc.)
  * Shows a legend: green = Available, amber = Held, violet = Selected, gray = Sold
  * Shows `LoadingSpinner` while the first fetch is in progress
* \[ ] Create `src/components/customer/HoldTimer.tsx`:

  * Props: `expiresAt: number` (Unix ms timestamp)
  * Displays "Hold expires in MM:SS" counting down every second
  * Uses `setInterval` in a `useEffect`
  * On expiry: shows a modal overlay with "Your hold has expired" message and
a "Back to seat selection" button
  * Timer bar: thin progress bar depleting from full to empty over 5 minutes
* \[ ] Create `src/components/customer/SelectionSummary.tsx`:

  * Props: `selectedSeats: SeatResponse\[]`, `pricePerSeat: number`
  * Lists each selected seat with its seat number and price
  * Shows total
  * "Proceed to Checkout" button that is disabled if no seats selected

\---

### Block 7 — Developer Mode components

* \[ ] Create `src/components/developer/DeveloperToggle.tsx`:

  * The `DEV` pill in the Navbar (already referenced in Block 5 Navbar)
  * When clicked: toggles `developerModeStore.enabled`
* \[ ] Create `src/components/developer/DeveloperConsole.tsx`:

  * Conditionally rendered when `developerModeStore.enabled === true`
  * Slides in from the right using Framer Motion `panelSlideIn` animation
  * Fixed position, right edge, vertically centered, z-index above page content
  * Width: 280px, dark background (`surface-overlay`), border on left side
  * Header: "Developer Console" + the ON/OFF toggle
  * Reads `developerModeStore.events\[0]` (most recent event) to populate widgets
  * Contains five widget sections (see individual widget tasks below)
  * Footer: "View Full Logs" button that expands the event timeline
* \[ ] Create `src/components/developer/RedisLockWidget.tsx`:

  * Shows: Status badge, Key (monospace), TTL (countdown from event timestamp),
Node (`redis-01` hardcoded)
  * Derives data from the most recent `DevEvent.redisLock`
* \[ ] Create `src/components/developer/DatabaseWidget.tsx`:

  * Shows: Status badge, Transaction ID (monospace, or "—" if null)
  * Derives data from `DevEvent.database`
* \[ ] Create `src/components/developer/CacheWidget.tsx`:

  * Shows: Status badge (UPDATED / MISS), Cache Hit label
  * Derives data from `DevEvent.cache`
* \[ ] Create `src/components/developer/KafkaWidget.tsx`:

  * Shows: Status badge (WAITING / PUBLISHED), Topic name (monospace)
  * Derives data from `DevEvent.kafka`
* \[ ] Create `src/components/developer/ApiCallWidget.tsx`:

  * Shows: HTTP method chip (POST in violet), path (monospace), status code
in green, latency in mono
  * Derives data from `DevEvent.apiCall`
* \[ ] Create `src/components/developer/EventTimeline.tsx`:

  * Renders `developerModeStore.events` as a scrollable list
  * Each row: timestamp (mono, relative "2s ago"), event type, key metric
  * Example row: `13:08:02  SEAT\_HOLD  A12  Redis ACQUIRED  152ms`
  * Maximum 50 events displayed
  * Clear button at the top

\---

### Block 8 — Customer Portal pages

* \[ ] Create `src/pages/customer/HomePage.tsx`:

  * Fetches `/api/events` on mount
  * Displays a hero section: "Preventing Seat Overselling Through Distributed Locking"
  * Quick stats row: total events (from array length), hardcoded "Active Locks" from
`developerModeStore.events` count or `0`
  * Event cards grid: one card per event from the API response
  * Each card shows event name, venue, city, date, and a "Book Now" button
linking to `/events/{eventId}`
  * Shows `LoadingSpinner` while fetching
* \[ ] Create `src/pages/customer/EventPage.tsx`:

  * Route: `/events/:eventId`
  * Left panel: `SeatGrid` component
  * Right panel: `SelectionSummary` component
  * Bottom: "Hold expires in" timer (only visible after holds are acquired)
  * On "Proceed to Checkout": for each selected seat, fire `useSeatHold` mutation.
Hold all seats sequentially (not in parallel — prevents rate limit hit).
If any hold fails, show error and stop. If all succeed, navigate to `/checkout`.
  * `DeveloperConsole` appears here when Developer Mode is enabled
* \[ ] Create `src/pages/customer/CheckoutPage.tsx`:

  * Route: `/checkout`
  * Displays order summary: held seat numbers, price per seat, platform fee
(₹149 hardcoded), taxes (₹99 hardcoded), total
  * The `HoldTimer` countdown is visible here
  * "Proceed to Payment" button navigates to `/payment`
  * "Edit Seats" link navigates back to `/events/{eventId}` (clears holds — not
implemented; seats will auto-expire)
* \[ ] Create `src/pages/customer/PaymentPage.tsx`:

  * Route: `/payment`
  * Displays payment method selection UI (UPI, Card, Net Banking — all stubbed)
  * "Confirm Booking" button calls `useBookingConfirm`
  * On success: navigate to `/booking-confirmed/{bookingId}`
  * On failure (409 hold expired): show error, redirect to event page
  * On failure (network): show retry button (uses same idempotency key)
  * `HoldTimer` countdown is visible here
* \[ ] Create `src/pages/customer/BookingConfirmedPage.tsx`:

  * Route: `/booking-confirmed/:bookingId`
  * Shows success checkmark animation
  * Booking ID in monospace
  * Seat numbers confirmed
  * "Go to My Bookings" button
  * "Book More Seats" button back to homepage
* \[ ] Create `src/pages/customer/MyBookingsPage.tsx`:

  * Route: `/my-bookings`
  * Reads `sessionStore.lastBookingId`
  * If a booking exists, display it in a card format
  * If no bookings, show empty state: "No bookings yet — browse events to get started"

\---

### Block 9 — Admin Dashboard pages

* \[ ] Create `src/components/admin/AdminSidebar.tsx`:

  * Left sidebar with navigation links:

    * Overview (`/admin`)
    * Redis (`/admin/redis`)
    * Kafka (`/admin/kafka`)
    * Cache (`/admin/cache`)
    * Circuit Breakers (`/admin/circuit-breakers`)
    * System Health (`/admin/health`)
  * Active link highlighted in brand-primary
  * "Back to Customer Portal" link at the bottom
* \[ ] Create `src/pages/admin/DashboardPage.tsx`:

  * Route: `/admin`
  * Uses `useOverviewMetrics()` hook (polls every 5s)
  * Top row: four `MetricCard` components:

    * Total Bookings: `overview.totalBookings`
    * Hold Success Rate: `overview.holdSuccessRate` formatted as percent
    * Holds Expired: `overview.holdsExpired`
    * Avg Hold Latency: `overview.avgHoldLatencyMs` formatted with `formatMs()`
  * Second row: Booking trend chart (recharts `AreaChart`) — currently shows
the live counter value; build the trend by accumulating values over time
in a `useRef` array (add current value every 5 seconds, keep last 60 points)
  * Circuit breaker status row: small status badges for `redisLock` and `redisCache`
sourced from `useCircuitBreakers()` hook
* \[ ] Create `src/pages/admin/RedisPage.tsx`:

  * Route: `/admin/redis`
  * Uses `useRedisMetrics()` hook
  * Connection status: large `StatusBadge` (CONNECTED / DISCONNECTED)
  * Memory usage: `redis.memoryUsedBytes` formatted with `formatBytes()`
  * Total keys: `redis.totalKeys` formatted with `formatCount()`
  * Hit ratio: `redis.hitRatio` as circular gauge (CSS `conic-gradient` or
Recharts `RadialBarChart`)
  * Active locks: `redis.activeLocks` — the count of `seat:lock:\*` keys
currently in Redis (live seats being held right now)
  * Connected clients: `redis.connectedClients`
* \[ ] Create `src/pages/admin/CachePage.tsx`:

  * Route: `/admin/cache`
  * Uses `useCacheMetrics()` hook
  * Hit ratio gauge (same style as Redis page)
  * Cache hits counter and misses counter
  * Active cache keys count: `cache.activeCacheKeys` (number of
`seats:event:\*` keys currently in Redis)
  * Trend chart: accumulate hit ratio values over time (same `useRef` approach
as the booking trend chart)
* \[ ] Create `src/pages/admin/CircuitBreakersPage.tsx`:

  * Route: `/admin/circuit-breakers`
  * Uses `useCircuitBreakers()` hook
  * One card per circuit breaker (`redisLock`, `redisCache`)
  * Each card shows: name, state badge (CLOSED/OPEN/HALF\_OPEN), failure rate
percent, failed calls count, successful calls count
  * State badge color: green for CLOSED, red for OPEN, amber for HALF\_OPEN
* \[ ] Create `src/pages/admin/KafkaPage.tsx`:

  * Route: `/admin/kafka`
  * Uses `useOverviewMetrics()` (no dedicated Kafka hook yet)
  * Currently the backend does not expose granular Kafka metrics — display a
"Kafka topics active" indicator and a note that consumer lag is visible
in Grafana. This page is a placeholder for future expansion.

\---

### Block 10 — App router and providers

* \[ ] Create `src/App.tsx`:

  * Wraps the entire app in `QueryClientProvider` (TanStack Query)
  * Wraps in `BrowserRouter` (React Router)
  * Applies theme class to `document.documentElement` via `useTheme()` hook
  * Renders `DeveloperConsole` at the root level (outside route outlets) so it
persists across page navigations
  * Route definitions:

&#x20;   ```
    /                    → HomePage
    /events/:eventId     → EventPage
    /checkout            → CheckoutPage
    /payment             → PaymentPage
    /booking-confirmed/:bookingId → BookingConfirmedPage
    /my-bookings         → MyBookingsPage
    /admin               → DashboardPage (with AdminSidebar layout)
    /admin/redis         → RedisPage
    /admin/kafka         → KafkaPage
    /admin/cache         → CachePage
    /admin/circuit-breakers → CircuitBreakersPage
    /admin/health        → SystemHealthPage
    ```

* \[ ] Create `src/main.tsx`:

  * Standard React 19 root render
  * Wraps `<App />` in `<StrictMode>`

\---

### Block 11 — Integration testing and polish

* \[ ] Full booking flow end-to-end test (manual):

  1. Open `http://localhost:5173`
  2. Click "Book Now" on Test Concert
  3. Select seat A1 on the seat grid
  4. Click "Proceed to Checkout"
  5. Verify seat A1 is now HELD (amber) on the grid if you open a second tab
  6. Complete payment → verify Booking Confirmed page shows booking ID
  7. Verify seat A1 is CONFIRMED (gray) on the grid in the second tab
* \[ ] Enable Developer Mode and verify console updates at each step:

  * After step 4: console shows Redis ACQUIRED, Kafka WAITING
  * After step 6: console shows Redis DELETED, Database COMMITTED, Kafka PUBLISHED
* \[ ] Open `/admin` and verify all metric cards show live data (non-zero after
performing the booking flow)
* \[ ] Verify dark/light theme toggle works on every page
* \[ ] Verify the hold timer counts down and shows expiry modal after 5 minutes
(test with a shortened TTL temporarily if needed)
* \[ ] Test error states:

  * Stop the Spring Boot app → verify the frontend shows appropriate error
messages rather than crashing
  * Hold all 100 seats → verify subsequent hold attempts show 409 message
  * Fire 6 rapid holds → verify the 6th shows rate limit message

\---

## Part 7 — File Structure Summary

```
SeatLock/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── postcss.config.js
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── index.css
│       ├── types/
│       │   ├── api.ts
│       │   └── metrics.ts
│       ├── lib/
│       │   ├── api.ts
│       │   ├── formatters.ts
│       │   └── constants.ts
│       ├── store/
│       │   ├── themeStore.ts
│       │   ├── sessionStore.ts
│       │   └── developerModeStore.ts
│       ├── hooks/
│       │   ├── useTheme.ts
│       │   ├── useMetricStream.ts
│       │   ├── useMetrics.ts
│       │   ├── useSeatHold.ts
│       │   └── useBookingConfirm.ts
│       ├── components/
│       │   ├── common/
│       │   │   ├── Navbar.tsx
│       │   │   ├── StatusBadge.tsx
│       │   │   ├── MetricCard.tsx
│       │   │   ├── ThemeToggle.tsx
│       │   │   └── LoadingSpinner.tsx
│       │   ├── customer/
│       │   │   ├── SeatGrid.tsx
│       │   │   ├── SeatCell.tsx
│       │   │   ├── HoldTimer.tsx
│       │   │   └── SelectionSummary.tsx
│       │   ├── developer/
│       │   │   ├── DeveloperConsole.tsx
│       │   │   ├── DeveloperToggle.tsx
│       │   │   ├── RedisLockWidget.tsx
│       │   │   ├── DatabaseWidget.tsx
│       │   │   ├── CacheWidget.tsx
│       │   │   ├── KafkaWidget.tsx
│       │   │   ├── ApiCallWidget.tsx
│       │   │   └── EventTimeline.tsx
│       │   └── admin/
│       │       ├── AdminSidebar.tsx
│       │       └── MetricCard.tsx (re-exports common/MetricCard)
│       └── pages/
│           ├── customer/
│           │   ├── HomePage.tsx
│           │   ├── EventPage.tsx
│           │   ├── CheckoutPage.tsx
│           │   ├── PaymentPage.tsx
│           │   ├── BookingConfirmedPage.tsx
│           │   └── MyBookingsPage.tsx
│           └── admin/
│               ├── DashboardPage.tsx
│               ├── RedisPage.tsx
│               ├── CachePage.tsx
│               ├── CircuitBreakersPage.tsx
│               ├── KafkaPage.tsx
│               └── SystemHealthPage.tsx
│
└── src/main/java/com/ShikharKothari0/SeatLock/
    ├── config/
    │   └── CorsConfig.java               ← new
    ├── controller/
    │   └── AdminController.java          ← new
    ├── service/
    │   └── AdminMetricsService.java      ← new
    └── dto/
        └── admin/                        ← new package
            ├── MetricsOverviewResponse.java
            ├── CacheMetricsResponse.java
            ├── RedisMetricsResponse.java
            ├── KafkaMetricsResponse.java
            ├── CircuitBreakerMetricsResponse.java
            ├── SystemHealthResponse.java
            └── MetricStreamSnapshot.java
```

\---

## Part 8 — Quick Reference: What the Frontend Consumes

|URL|Method|Used by|Polling interval|
|-|-|-|-|
|`/api/events`|GET|HomePage|On mount|
|`/api/events/{id}`|GET|EventPage|On mount|
|`/api/events/{id}/seats`|GET|SeatGrid|Every 5s|
|`/api/seats/{id}/hold`|POST|useSeatHold|On user action|
|`/api/bookings/confirm`|POST|useBookingConfirm|On user action|
|`/api/admin/metrics/overview`|GET|DashboardPage|Every 5s|
|`/api/admin/metrics/cache`|GET|CachePage|Every 5s|
|`/api/admin/metrics/redis`|GET|RedisPage|Every 5s|
|`/api/admin/metrics/circuit-breakers`|GET|CircuitBreakersPage|Every 5s|
|`/api/admin/health`|GET|SystemHealthPage|Every 10s|
|`/api/admin/metrics/stream`|GET (SSE)|DeveloperConsole, DashboardPage|Continuous|




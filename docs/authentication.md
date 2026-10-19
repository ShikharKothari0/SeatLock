# Authenticated reservation ownership

Implemented 16 October 2026, after checkpoint commit `b384557` (intentionally empty message). These authentication changes are a separate, uncommitted working-tree milestone.

## Using the application

Open http://127.0.0.1:5173/login and choose **Create an account**. Register with an email, display name and password of at least 12 characters (maximum 72 UTF-8 bytes). Registration creates a customer account. Successful sign-in returns to the protected page that prompted it. Event listings are public; the frontend requires sign-in before entering the reservation journey.

There is no shared password or automatic login for the old seeded demo identity. Its bookings remain in the database, but are not assigned to new accounts. Previously persisted browser state is cleared when the authenticated account differs. Refresh preserves same-account pending confirmation requests; session expiry requires sign-in before continuing. Sign-out clears local reservation state and invalidates the server session. Existing unconfirmed holds remain until their deadline; sign in again to recover or release them.

Administrator access is an explicit database operation. Register the intended account normally, then promote its verified UUID from a trusted database session:

```sql
UPDATE app_user SET role = 'ADMIN' WHERE id = '<registered-account-uuid>';
```

Sign out and back in after changing roles. Roles are captured in the authenticated session; role changes do not revoke existing sessions immediately. No personal account was promoted during implementation. Live tests create their own dedicated administrator account with a random password held only in the test process.

## API contract

| Endpoint | Contract |
| --- | --- |
| GET /api/auth/csrf | Public. Starts/uses a session and returns headerName/token. |
| POST /api/auth/register | CSRF-protected JSON: email, displayName, password. Returns 201. Client roles are ignored. |
| POST /api/auth/login | CSRF-protected form data: email, password. Returns 204 and rotates the session ID; invalid credentials return 401. |
| GET /api/auth/me | Returns authenticated id, email, displayName and role; otherwise 401. |
| POST /api/auth/logout | CSRF-protected. Invalidates session and deletes its cookie; returns 204. |
| Reservation endpoints | Require a session; all ownership is taken from the principal. Supplied userId values cannot select another owner. |
| /api/admin/** | Requires ADMIN; customers receive 403. |
| /actuator/** | Requires ADMIN except basic /actuator/health. Anonymous health omits component details. |

Hold creation no longer needs a body. Active holds, release, history and receipt requests no longer need a userId query parameter. Confirmation accepts seatIds and idempotencyKey. Internal service methods still receive the principal's UUID and retain ownership checks. Foreign receipts remain 404; forbidden releases/confirmations remain 409 according to the existing reservation contracts.

The frontend obtains a fresh CSRF token before every write. It never retries a mutation merely because authentication failed. Tokens must be refreshed after login/logout; see the [Spring Security 6.5 CSRF documentation](https://www.springframework.org/spring-security/reference/6.5/servlet/exploits/csrf.html). Passwords use BCrypt strength 12. V6 adds nullable password hashes and CUSTOMER/ADMIN roles plus normalized email claims; old demo accounts are not enabled for password login. Registration is transactional, including its unique normalized email claim.

## Runtime and deployment boundaries

- Sessions use an HttpOnly, SameSite=Lax cookie, with a 30-minute inactivity timeout. They are local to the backend process and are lost on restart. Multi-instance deployment needs shared sessions or a deliberately configured routing strategy.
- Set `SEATLOCK_COOKIE_SECURE=true` for HTTPS deployment. Local HTTP uses false. Production CORS origins also need explicit configuration.
- Login and registration share a bounded per-instance limit of 30 attempts per remote address per minute. A gateway/shared limiter is needed for multiple replicas and trusted proxy address handling. Forwarded headers are not blindly trusted.
- The existing unauthenticated Prometheus scrape now receives 401. Deployment needs a dedicated authenticated monitoring arrangement before its dashboards can be considered operational. The signed-in admin API remains available.
- Email verification, password reset/change, explicit session revocation and account administration are not implemented. This milestone is not a production identity-service certification.

## Validation

- Five isolated PostgreSQL/Redis authentication integration tests cover real registration/login, BCrypt storage, duplicate/invalid registration, CSRF, session fixation protection, logout, customer/admin boundaries and spoofed ownership across hold/release/confirmation/history/receipt APIs.
- One unit test verifies the authentication attempt limit and public-read behavior.
- The prior ten hardening, four reservation-contract and one CORS tests passed with the security dependency enabled.
- Frontend production build and 17 unit tests passed. Eight controlled browser regression scenarios passed with authenticated fixtures.
- Four real-browser authentication checks and six real-service reservation recovery scenarios passed with separate registered accounts. The authentication browser checks exercise registration, protected-page return, reload/cookies, customer rejection from admin, incorrect credentials and server-invalidated sessions. The test simulates session expiry by invalidating a session; it does not wait for the full 30-minute inactivity timeout.
- All ten full live booking checks passed with authenticated customers/admin, including metrics-stream reconnects, browser booking, contention, database/Redis agreement, natural five-minute expiry and Kafka records. Retained event: `ccb805d7-d5ea-4887-94b6-bdbec709f6ff`. Total: 21 targeted backend tests across focused runs, 17 frontend unit tests, eight controlled browser scenarios and 20 real-service/browser checks. Frontend lint also passed.

Commands from the repository root (Java 21 and Docker):

```powershell
.\mvnw.cmd '-Dtest=AuthenticationTest,AuthRateLimitFilterTest,ReservationHardeningTest,ReservationContractTest,CorsConfigTest' test
```

From frontend/, with backend on 8080 and Vite on 127.0.0.1:5173:

```powershell
npm run build
npm test
$env:SEATLOCK_LIVE_TEST = '1'
$env:BROWSER_CHANNEL = 'msedge'
npm run test:auth
npm run test:recovery
npm run test:live
```

Live scripts retain dedicated accounts/events/bookings, use random credentials without saving passwords, and authenticate separate API/browser sessions. Reports are ignored under frontend/test-results/; logs are under target/auth-*.log. The full live suite still waits for natural five-minute expiry.

## Next product milestone

Move pricing and totals to a server-owned quote, persist the accepted amounts/currency with the booking, and render the same authoritative total in selection, checkout and receipts. Define quote expiry and stale-price behavior before introducing a payment-provider sandbox and webhook processing.

# Packaged deployment

For the current zero-cost, temporary public-demo objective, use the [Vercel/Render/Aiven runbook](temporary-demo.md). This document describes the separate full-stack Docker deployment used for local validation and optional VM hosting.

The separate `compose.deploy.yml` builds a Java 21 runtime image and a compiled React/Caddy image. It runs PostgreSQL, Redis, Kafka and ZooKeeper on the Compose network without published dependency/backend ports. Only the web proxy is published. The default bindings are loopback, preserving the existing development stack and data.

## Local validation

Copy `deploy/env.example` to `.env.deploy`, replace the database password with a strong random value, then run from the repository root:

```powershell
docker compose --env-file .env.deploy -f compose.deploy.yml up -d --build --wait --wait-timeout 300
```

Open http://localhost:8088. This is a separate database; development accounts are not copied. Register a new account. To grant a specific registered account admin access, use `psql` inside this deployment's PostgreSQL container to update that account's role to ADMIN, then sign out/in. Never expose public role-assignment endpoints or add a default administrator password.

The deployment has project-scoped persistent volumes for PostgreSQL, Redis AOF, Kafka, ZooKeeper and Caddy certificates. Normal `down` preserves these; `down -v` deletes them. Do not use it on data you need. Session storage remains in the backend process, so users sign in again after backend recreation. This configuration targets one backend instance, not horizontally scaled session handling.

## Public HTTPS

On a host you control, set DNS for your domain, allow inbound TCP 80/443, and set:

```dotenv
SITE_ADDRESS=tickets.your-domain.example
PUBLIC_ORIGIN=https://tickets.your-domain.example
COOKIE_SECURE=true
BIND_ADDRESS=0.0.0.0
HTTP_PORT=80
HTTPS_PORT=443
```

Replace the example domain. Caddy obtains and renews public certificates when DNS/network access permit; this is not verified until run on the actual host. Retain its data volume. Never deploy the local HTTP/cookie settings publicly. Backend cookies are HttpOnly and SameSite=Lax; HTTPS uses Secure cookies. The proxy serves static assets and forwards `/api/` on the same origin. Browser deep links fall back to the SPA. Public `/actuator/` is blocked; backend health is checked inside Docker. Caddy replaces forwarded connection headers, and backend forwarding support is enabled only in the deployment profile. Keep the backend port private.

The configured allowed-origin list replaces the local development CORS defaults. CSRF and account/admin authorization remain enabled. Database credentials live in the ignored environment file, not image layers. Restrict that file's permissions; Docker operators can inspect container environment. Changing the environment password does not rotate an existing PostgreSQL role password: rotate it in PostgreSQL as well.

## Validation commands

```powershell
Set-Location frontend
$env:SEATLOCK_LIVE_TEST='1'
$env:SEATLOCK_DEPLOY_TEST='1'
$env:SEATLOCK_TEST_URL='http://localhost:8088'
$env:BROWSER_CHANNEL='msedge'
npm run test:catalog
```

This creates dedicated accounts, a venue, an event and bookings in the deployment database. The script intentionally retains its test records. Do not aim it at a public production database.

## Recovery and operations

Run `./scripts/Backup-Database.ps1 -Deployment` to back up this deployment. Without that switch the script targets the development database. Restore verification accepts either database's PostgreSQL 16 archive with its checksum manifest. For promotion, mount the recovered volume as the deployment's `database` volume after following the maintenance/password/cache steps in [database recovery](database-recovery.md); do not change the development `.env` as a substitute for changing this deployment's volume configuration.

Persistent Redis/Kafka volumes improve restart retention but do not provide coordinated point-in-time recovery. Off-host backups, automated CI, image vulnerability review/digest pinning, host monitoring, real-host TLS validation and outage/load checks remain pre-launch work. This is a single-host portfolio deployment, not a high-availability ticketing platform. Demo payments stay explicitly labelled; no gateway is needed for this scope.

References: [Caddy HTTPS](https://caddyserver.com/docs/quick-starts/https), [Caddy proxy behavior](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [Spring Boot forwarding](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html).

## Local evidence — 19 October 2026

Both images built successfully. Five real browser catalogue checks passed through the packaged proxy, covering admin draft creation/edit/preview, publication, customer booking and receipt reload, authoritative pricing/idempotency and sales closure. `frontend/tests/deployment.mjs` passed separately over HTTP and local HTTPS, checking deep-link reload, API routing, blocked public Actuator, administrator protection, CSRF, rejected foreign origins and HttpOnly/SameSite/Secure cookie flags. Local HTTPS used Caddy's local certificate and the test trusted it explicitly; this does not establish public certificate issuance or browser trust on a real domain.

The deployment backup restored 3 accounts, 2 events, 2 bookings, 2 payments and 9 migrations into an isolated database. The local preview is returned to HTTP at `http://localhost:8088` after the TLS test. The existing development data was not migrated or replaced. Image builds skip tests; the live and smoke checks above are separate evidence, not a claim that a full CI suite ran.

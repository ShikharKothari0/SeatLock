# Temporary public demo and portfolio evidence

Goal: run the real booking system publicly long enough to record it, retain screenshots and verified engineering evidence, then shut down unused hosting. No paid hosting is authorized. Local recordings are rehearsals, not proof of public deployment.

## Current preparation

Local validation completed: three Kafka configuration tests and the Vercel generator checks passed. All five catalogue/browser checks passed against a backend capped at 512 MiB. Reviewed screenshots and the report are in [demo evidence](demo/README.md). Raw videos were captured but are not a finished public walkthrough. Aiven PostgreSQL, Valkey and Kafka services and the Render account are now created. The five Kafka topics have two partitions each, verified from the service screenshot. Private connection configuration, remote TLS/Valkey/Kafka checks, Vercel setup and public recording remain outstanding.

- `render.yaml`: one free backend web service in Singapore, near the Aiven PostgreSQL and Valkey services in Bangalore; no paid worker/database resources.
- `application-cloud-demo.properties`: small connection pools, secure cookies via the deployment profile, authenticated/TLS Kafka and Redis, externally managed Kafka topics.
- `scripts/Configure-Vercel.ps1`: generates the frontend proxy configuration from the actual HTTPS Render origin.
- `compose.demo-check.yml`: local 512 MB backend memory check. This does not reproduce Render CPU, latency, networking or cold starts.
- `SEATLOCK_CAPTURE_DEMO=1`: catalogue test records separate admin/customer browser videos and extra screenshots. These are automated walkthroughs with synthetic records, no real payments.

## Account and service setup

1. Create an [Aiven account](https://console.aiven.io/). Select explicitly **Free** PostgreSQL, Valkey and Kafka services, one of each. Avoid a paid-plan trial. Record the assigned regions before selecting the backend region. Aiven free services can shut down after inactivity; check their state before recording.
2. Create these Kafka topics in Aiven, each with **two partitions**, using the provider's replication defaults: `seat-held`, `seat-confirmed`, `seat-released`, `seat-confirmed.DLT`, `seat-released.DLT`. This consumes all five free topics. Do not introduce extra test topics. The cloud profile disables application-driven topic administration.
3. Create a [Render account](https://dashboard.render.com/). Connect the repository containing these changes and use `render.yaml`, with the **Free** plan. Select the closest available region to the Aiven services. The repository must contain the deployment files; uncommitted local changes are not visible to Render.
4. Provide the backend secrets through Render's environment settings (see below). Never put them in Git, screenshots or browser-side `VITE_*` variables.
5. Create a [Vercel account](https://vercel.com/signup) on Hobby. Use `frontend` as project root and Vite as the framework. Once Render assigns its origin, run `./scripts/Configure-Vercel.ps1 -BackendUrl https://YOUR-SERVICE.onrender.com` locally. Include the resulting `frontend/vercel.json` in the deployment source before building Vercel.
6. Set Render's `SEATLOCK_ALLOWED_ORIGINS` to the exact stable HTTPS Vercel origin and redeploy the backend. Use that stable deployment for recording; arbitrary preview domains are not automatically allowed. Validate the generated proxy and cookies end to end before claiming the public flow works.

## Backend secret configuration

`render.yaml` prompts for the required values:

| Variable | Value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL with provider host, port/database and `sslmode=verify-full&sslrootcert=/etc/secrets/postgres-ca.pem` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Aiven PostgreSQL credentials |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | Aiven Valkey endpoint |
| `SPRING_DATA_REDIS_USERNAME`, `SPRING_DATA_REDIS_PASSWORD` | Aiven Valkey credentials; use the provider's actual username |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Aiven SASL endpoint, including its port |
| `KAFKA_JAAS_CONFIG` | `org.apache.kafka.common.security.scram.ScramLoginModule required username="..." password="...";` using provider credentials, with special characters escaped as required by JAAS |
| `KAFKA_CA_PEM` | Full Kafka CA certificate PEM, including BEGIN/END lines |
| `SEATLOCK_ALLOWED_ORIGINS` | Exact stable HTTPS frontend origin |

Enable/use SCRAM-SHA-256 on the Kafka service, matching the cloud profile. Upload the PostgreSQL CA as a Render secret file named `postgres-ca.pem`. For Valkey, use a provider endpoint with a CA trusted by the JVM or configure a JVM truststore with the supplied CA via secret files and `JAVA_TOOL_OPTIONS`. Do not disable TLS hostname or certificate verification. Exact account endpoints/authentication options are still to be checked after signup.

The custom failure-topic Kafka producer now inherits Spring's Kafka properties, including TLS/SASL. Both ordinary event delivery and failure delivery must be tested against Aiven. Valkey's Redis compatibility must be checked with SeatLock's actual Lua scripts and contention tests, not just a connection ping.

## Recording and evidence

Record a 2–3 minute human-paced public walkthrough once the hosted flow passes:

1. Browse an event and show its details.
2. Select seats, review the price and confirm a clearly labelled demo payment.
3. Refresh the receipt and show booking history.
4. Briefly show the administrator's draft editor and publication workflow.

Separately capture engineering evidence: two customers competing for a seat; expiry/recovery; idempotent confirmation; Kafka delivery/retry. Only describe load numbers that a retained test report establishes. Do not equate sequential UI checks with a concurrency benchmark.

Local rehearsal command, from `frontend`:

```powershell
$env:SEATLOCK_LIVE_TEST='1'
$env:SEATLOCK_DEPLOY_TEST='1'
$env:SEATLOCK_TEST_URL='http://localhost:8088'
$env:SEATLOCK_CAPTURE_DEMO='1'
$env:BROWSER_CHANNEL='msedge'
npm run test:catalog
```

This script currently uses local Docker for dedicated test-account role setup. It is not a remote deployment script. Raw local videos and screenshots are saved under ignored `frontend/test-results/`. Review them, trim pauses and add narration before uploading. Keep raw recordings and account state out of Git. Publish selected reviewed images and a video link only, with synthetic customer data.

## Completion and shutdown

- Record the public URL, date, deployed revision, test results and any cold-start limitations.
- Update the README with the recorded walkthrough, screenshots, architecture and reproducible local setup. Link the full evidence separately from the short LinkedIn description.
- Review the LinkedIn post and assets before publishing; no post is sent automatically.
- Back up any useful demo records, then delete or suspend the temporary services using each provider's controls. Remove unused credentials and deployment integrations. Verify there are no paid services or active trial upgrades.
- Replace the live link with “Recorded demo” once hosting stops. Do not leave an inactive URL presented as a working deployment.

References: [Render Blueprint configuration](https://render.com/docs/blueprint-spec), [Vercel rewrites](https://vercel.com/docs/routing/rewrites), [Aiven free Kafka](https://aiven.io/docs/products/kafka/free-tier/kafka-free-tier).

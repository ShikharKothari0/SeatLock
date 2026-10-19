# PostgreSQL persistence and recovery

Implemented and verified locally on 19 October 2026. PostgreSQL is the durable source of accounts, catalogue, seats/reservations, quotes, bookings, demo payments, audit records and the transactional outbox. This milestone covers the database, not a production deployment or a complete Kafka/Redis disaster-recovery system.

## Storage setup

From the repository root, run this once before starting Compose on a new installation or updating an existing installation:

```powershell
./scripts/Initialize-Storage.ps1
docker compose up -d
```

The initializer inspects the existing PostgreSQL container (including a stopped container) and records its actual data volume in the ignored `.env` file. Existing local installations therefore keep their current data rather than switching to an empty volume. With no existing container it provisions `seatlock_postgres_data`. If `.env` already points at missing storage it stops instead of silently creating an empty database.

Compose explicitly mounts that external volume at `/var/lib/postgresql/data`, waits for a PostgreSQL health check when invoked with `--wait`, and restarts PostgreSQL unless deliberately stopped. External storage is outside Compose's volume lifecycle. Explicit Docker volume removal, pruning unused volumes, Docker Desktop factory reset or disk failure can still destroy it. A volume is not a backup.

## Create and verify a backup

```powershell
./scripts/Backup-Database.ps1
./scripts/Test-DatabaseRestore.ps1 -BackupPath 'D:\Projects\SeatLock\backups\<returned-file>.dump'
```

The first command prints the archive path. It uses PostgreSQL 16 `pg_dump` custom format, copies the binary file without PowerShell stream conversion and writes a SHA-256 manifest beside it. A live dump is transactionally consistent but only contains data committed at its snapshot; later bookings require a later backup. It is a database backup, not a cluster-role or server-configuration backup.

The verifier checks the manifest/hash, starts PostgreSQL with no network or published ports, restores in a single transaction with errors fatal, queries key tables, and reads the full restored database back through `pg_dump`. It writes a restore-check JSON beside the archive and removes the temporary container/storage. A successful result is recovery evidence; a checksum alone is not.

Keep the archive and manifest together. They contain account password hashes and customer/booking data. `backups/` and `.env` are excluded from Git. Store a protected copy on a different device or encrypted remote storage; local backups alone do not protect against disk loss. Take a backup before migrations and regularly while the demo is used. Automatic scheduling, encryption and off-device transfer are not configured by these scripts.

## Recover into durable, separate storage

```powershell
./scripts/Restore-Database.ps1 -BackupPath 'D:\Projects\SeatLock\backups\<file>.dump' -RecoveryVolume seatlock_recovery_20261019
```

The recovery volume must be new. Existing volumes are rejected. The restore container has no network access, is removed afterward, and the named recovery volume is retained. On failure, treat any retained volume as incomplete and use a fresh name on retry. This command never overwrites or switches the live database.

To promote a verified recovery during maintenance:

1. Stop the backend and any other writers. Back up the current database if accessible. Record the old volume name so rollback remains possible.
2. Stop the Compose PostgreSQL service. Before exposing the recovery volume, start an isolated PostgreSQL 16 container on it and set the `admin` role password interactively with `psql` and `\password admin`. The initial recovery-only password is `restore-isolated-only`; replace it with the credential configured for the backend. Stop/remove that temporary container before attaching the volume elsewhere. Never mount the same PostgreSQL data volume into two running database servers.
3. Change only `SEATLOCK_POSTGRES_VOLUME` in `.env` to the verified recovery volume; run `docker compose up -d --no-deps --wait postgres`.
4. Invalidate SeatLock Redis caches/locks before starting the backend. The current Compose Redis is dedicated to SeatLock; flushing it also discards sessions and temporary state, so do this only during recovery maintenance. Never flush a shared Redis instance. Users must sign in again. Database reservation deadlines remain authoritative.
5. Start the backend; verify migrations, health, catalogue, booking history and a new demo booking. Check pending payment/outbox processing. Kafka may already contain events newer than the restored database or receive replayed events; consumers must tolerate duplicates and operators must reconcile this before treating a point-in-time recovery as production-ready.

No promotion or cache flush is performed by the scripts. PostgreSQL recovery does not restore browser state, Redis sessions or Kafka history. Redis/Kafka retention and coordinated recovery, production credentials/TLS, deployment packaging, automated backup delivery and broader outage tests remain separate work.

## Validation evidence

The first local backup restored 12 accounts, 6 events, 10 bookings, 8 payment attempts and 9 successful migrations. PostgreSQL container recreation retained identical full-row fingerprints for accounts, events, bookings and payments. Application health returned UP afterward. The original data volume was adopted in place; no live database was replaced and no live records were deleted.

Both disposable restore verification and restore into a separate persistent recovery volume passed. Negative checks rejected a damaged archive before container creation and refused to overwrite an existing recovery volume. Re-running storage initialization preserved the existing setting. The validation recovery volume `seatlock_recovery_validation_20261019` remains available locally; it is not attached to the running application.

Implementation references: [PostgreSQL 16 pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html), [PostgreSQL 16 pg_restore](https://www.postgresql.org/docs/16/app-pgrestore.html), and [Docker Compose external volumes](https://docs.docker.com/reference/compose-file/volumes/).

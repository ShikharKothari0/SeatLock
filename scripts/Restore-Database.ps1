# Restores into a disposable, network-isolated PostgreSQL container. Never touches the live DB.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$BackupPath, [string]$RecoveryVolume)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$archive = (Resolve-Path -LiteralPath $BackupPath).Path
$manifest = Get-Content -Raw -LiteralPath "$archive.json" | ConvertFrom-Json
if ($manifest.formatVersion -ne 1 -or $manifest.postgresMajor -ne 16 -or $manifest.database -ne 'seatlock') { throw 'Unsupported backup manifest.' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -ne $manifest.sha256) { throw 'Backup checksum mismatch.' }
$name = 'seatlock-restore-check-' + [guid]::NewGuid().ToString('N')
$started = $false
try {
    $storage = @('--tmpfs', '/var/lib/postgresql/data')
    if ($RecoveryVolume) {
        if ($RecoveryVolume -notmatch '^seatlock_recovery_[a-zA-Z0-9_-]+$') { throw 'Recovery volume must start with seatlock_recovery_ and contain only letters, digits, underscores and hyphens.' }
        $existing = @(docker volume ls --format '{{.Name}}')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing volumes.' }
        if ($RecoveryVolume -in $existing) { throw 'Recovery volume already exists; refusing to overwrite it.' }
        docker volume create $RecoveryVolume | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create recovery volume.' }
        $storage = @('--mount', "type=volume,source=$RecoveryVolume,target=/var/lib/postgresql/data")
    }
    docker run -d --name $name --network none @storage -e POSTGRES_PASSWORD=restore-isolated-only -e POSTGRES_USER=admin -e POSTGRES_DB=seatlock postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated restore container.' }
    $started = $true
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        docker exec $name pg_isready -U admin -d seatlock *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Restore database readiness timed out.' }
    docker cp $archive "${name}:/tmp/backup.dump"
    if ($LASTEXITCODE -ne 0) { throw 'Archive copy failed.' }
    docker exec $name pg_restore -U admin -d seatlock --exit-on-error --single-transaction --no-owner --no-acl /tmp/backup.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed.' }
    $sql = "SELECT 'accounts', count(*) FROM app_user UNION ALL SELECT 'events', count(*) FROM event UNION ALL SELECT 'bookings', count(*) FROM booking UNION ALL SELECT 'payments', count(*) FROM payment_attempt UNION ALL SELECT 'migrations', count(*) FROM flyway_schema_history WHERE success;"
    $counts = @(docker exec $name psql -U admin -d seatlock -v ON_ERROR_STOP=1 -At -c $sql)
    if ($LASTEXITCODE -ne 0) { throw 'Restored schema query failed.' }
    # Dump the restored DB again to force reading every restored table, including large values.
    docker exec $name pg_dump -U admin -d seatlock --format=custom --file=/tmp/roundtrip.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restored database could not be read back.' }
    [ordered]@{ verifiedUtc = [DateTime]::UtcNow.ToString('o'); backupSha256 = $manifest.sha256; result = 'PASS'; counts = $counts } |
        ConvertTo-Json | Set-Content -LiteralPath "$archive.restore-check.json" -Encoding utf8
    Write-Host 'PASS: isolated restore, constraints and full database read-back.'
    $counts | Write-Host
    if ($RecoveryVolume) { Write-Host "Recovered data retained in $RecoveryVolume. Replace the recovery-only password before attaching it to a network. The live application has not been switched." }
} finally {
    if ($started) { docker rm -f -v $name | Out-Null }
}

# Adopts existing PostgreSQL storage without copying, deleting or overwriting it.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $envFile = Join-Path $root '.env'
    if (Test-Path $envFile) {
        $setting = @(Get-Content $envFile | Where-Object { $_ -match '^SEATLOCK_POSTGRES_VOLUME=' })
        if ($setting.Count -gt 0) {
            if ($setting.Count -ne 1) { throw 'Duplicate storage settings in .env.' }
            $volume = $setting[0].Substring('SEATLOCK_POSTGRES_VOLUME='.Length).Trim()
            if ($volume -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { throw 'Invalid volume name in .env.' }
            docker volume inspect $volume *> $null
            if ($LASTEXITCODE -ne 0) { throw 'Configured storage is missing. Restore a backup; do not silently initialize an empty database.' }
            Write-Host "Storage already configured: $volume"
            return
        }
    }
    $ids = @(docker compose ps -a -q postgres)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Compose PostgreSQL container.' }
    if ($ids.Count -gt 1) { throw 'Multiple PostgreSQL containers found.' }
    if ($ids.Count -eq 1) {
        $inspect = docker inspect $ids[0] | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Container inspection failed.' }
        $mounts = @($inspect[0].Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' -and $_.Type -eq 'volume' })
        if ($mounts.Count -ne 1) { throw 'Expected one PostgreSQL data volume; inspect manually before proceeding.' }
        $volume = $mounts[0].Name
    } else {
        $volume = 'seatlock_postgres_data'
        docker volume create $volume | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create database volume.' }
    }
    Add-Content -LiteralPath $envFile -Value "`nSEATLOCK_POSTGRES_VOLUME=$volume"
    Write-Host "Configured persistent storage: $volume. Existing data has not been changed."
} finally { Pop-Location }

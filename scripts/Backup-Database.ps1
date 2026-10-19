[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'backups'),
    [switch]$Deployment
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
$container = $null
$remote = '/tmp/seatlock-' + [guid]::NewGuid().ToString('N') + '.dump'
try {
    $composeArgs = @('compose')
    if ($Deployment) { $composeArgs += @('--env-file', '.env.deploy', '-f', 'compose.deploy.yml') }
    $ids = @(docker @composeArgs ps -q postgres)
    if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1) { throw 'Exactly one running PostgreSQL container is required.' }
    $container = $ids[0]
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $destination = Join-Path (Resolve-Path $OutputDirectory).Path ("seatlock-{0}-{1}.dump" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), [guid]::NewGuid().ToString('N').Substring(0,8))
    docker exec $container pg_dump -U admin -d seatlock --format=custom --no-owner --no-acl --file=$remote
    if ($LASTEXITCODE -ne 0) { throw 'Database dump failed.' }
    # docker cp preserves binary archives on Windows; never pipe the dump through PowerShell.
    docker cp "${container}:$remote" $destination
    if ($LASTEXITCODE -ne 0) { throw 'Copying the archive failed.' }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash
    [ordered]@{ formatVersion = 1; createdUtc = [DateTime]::UtcNow.ToString('o'); database = 'seatlock'; postgresMajor = 16; sha256 = $hash } |
        ConvertTo-Json | Set-Content -LiteralPath "$destination.json" -Encoding utf8
    Write-Output $destination
} finally {
    if ($container) { docker exec $container rm -f $remote | Out-Null }
    Pop-Location
}

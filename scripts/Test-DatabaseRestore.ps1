[CmdletBinding()]
param([Parameter(Mandatory)][string]$BackupPath)
& (Join-Path $PSScriptRoot 'Restore-Database.ps1') -BackupPath $BackupPath

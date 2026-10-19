param([int]$TimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
$toolsDir = Join-Path $env:LOCALAPPDATA 'SeatLockTools'
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
$logPath = Join-Path $toolsDir 'docker-startup.log'
function Write-StartupLog([string]$Message) {
    "$(Get-Date -Format o) $Message" | Add-Content -LiteralPath $logPath
    Write-Output $Message
}
function Test-Engine {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = 'docker.exe'
    $info.Arguments = '--context desktop-linux info --format {{.ServerVersion}}'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    try {
        if (!$process.WaitForExit(8000)) { $process.Kill(); return $false }
        return $process.ExitCode -eq 0
    } finally { $process.Dispose() }
}
$mutex = New-Object System.Threading.Mutex($false, 'Local\SeatLockDockerStartup')
$locked = $false
try {
    try { $locked = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $locked = $true }
    if (!$locked) { Write-StartupLog 'Another guarded launch is already in progress.'; exit 0 }
    $desktopExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (!(Test-Path -LiteralPath $desktopExe)) { throw 'Docker Desktop is not installed at the expected location.' }
    if (Test-Engine) {
        Write-StartupLog 'Docker is already healthy; no socket folders changed.'
        Start-Process -FilePath $desktopExe -WindowStyle Hidden
        exit 0
    }
    $running = @(Get-Process -Name 'Docker Desktop','com.docker.backend' -ErrorAction SilentlyContinue)
    if ($running.Count -eq 0) {
        $localRoot = [IO.Path]::GetFullPath($env:LOCALAPPDATA).TrimEnd('\')
        foreach ($relative in @('Docker\run','docker-secrets-engine')) {
            $target = [IO.Path]::GetFullPath((Join-Path $localRoot $relative))
            if (!$target.StartsWith($localRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected runtime path.' }
            if (!(Test-Path -LiteralPath $target)) { continue }
            $folder = Get-Item -LiteralPath $target -Force
            $parent = Get-Item -LiteralPath (Split-Path $target -Parent) -Force
            if (($folder.Attributes -band [IO.FileAttributes]::ReparsePoint) -or ($parent.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Refusing redirected runtime directory: $target" }
            $entries = @(Get-ChildItem -LiteralPath $target -Force)
            if (!$entries.Count) { continue }
            # Only rotate directories consisting entirely of zero-byte socket reparse points.
            foreach ($entry in $entries) {
                if ($entry.PSIsContainer -or $entry.Length -ne 0 -or !($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                    throw "Unexpected runtime content in $target; leaving it untouched."
                }
            }
            if (@(Get-Process -Name 'Docker Desktop','com.docker.backend' -ErrorAction SilentlyContinue).Count) { throw 'Docker started concurrently; retry after it has stopped.' }
            $backupName = $folder.Name + '.stale-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
            Rename-Item -LiteralPath $target -NewName $backupName
            New-Item -ItemType Directory -Path $target -Force | Out-Null
            Write-StartupLog "Preserved stranded sockets: $target -> $backupName"
        }
        Start-Process -FilePath $desktopExe -WindowStyle Hidden
        Write-StartupLog 'Started Docker Desktop.'
    } else { Write-StartupLog 'Docker is starting or unhealthy; waiting without stopping processes or changing sockets.' }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Engine) { Write-StartupLog 'Docker engine is ready.'; exit 0 }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw 'Docker did not become ready. Quit Docker Desktop normally and run this launcher again; inspect Docker logs if it still fails.'
} catch {
    Write-StartupLog ('ERROR: ' + $_.Exception.Message)
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}

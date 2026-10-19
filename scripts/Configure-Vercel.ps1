[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackendUrl,
    [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend/vercel.json')
)
$ErrorActionPreference = 'Stop'
$uri = [uri]$BackendUrl
if (!$uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or !$uri.Host.EndsWith('.onrender.com') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/') {
    throw 'Provide the HTTPS Render service origin, without credentials, paths or query parameters.'
}
$origin = $uri.GetLeftPart([System.UriPartial]::Authority)
$config = [ordered]@{
    framework = 'vite'
    buildCommand = 'npm run build'
    outputDirectory = 'dist'
    rewrites = @(
        @{source='/api/:path*'; destination="$origin/api/:path*"},
        @{source='/:path*'; destination='/index.html'}
    )
}
$config | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Host 'Created Vercel configuration. Use frontend as the Vercel project root. The backend URL contains no secret.'

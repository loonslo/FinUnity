param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
Push-Location (Join-Path $taskRoot 'backend')
try {
    uv sync --locked --group dev
    if ($LASTEXITCODE -ne 0) { throw 'Python dependency setup failed.' }
} finally { Pop-Location }
Push-Location (Join-Path $taskRoot 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency setup failed.' }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
Push-Location (Join-Path $taskRoot 'backend')
try {
    Write-Host 'FinUnity local prototype: http://127.0.0.1:8011'
    if (-not $NoBrowser) {
        Start-Job -ScriptBlock {
            Start-Sleep -Seconds 3
            Start-Process 'http://127.0.0.1:8011'
        } | Out-Null
    }
    uv run --no-sync uvicorn app.prototype:app --host 127.0.0.1 --port 8011 --workers 1 --no-access-log
} finally { Pop-Location }

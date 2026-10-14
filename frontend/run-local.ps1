$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $env:VITE_DATA_MODE = 'local'
    if (-not (Test-Path -LiteralPath 'node_modules/.bin/vite.cmd')) {
        & npm.cmd ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw '프론트 의존성 설치가 실패했습니다.' }
    }
    & npm.cmd run dev
} finally {
    Pop-Location
}

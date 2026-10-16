$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$manifestPath = Join-Path $repoRoot "desktop\Cargo.toml"
$exePath = Join-Path $repoRoot "dist\SJTURunningMan.exe"

if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Missing Rust desktop manifest: $manifestPath"
}

cargo build --release --locked --manifest-path $manifestPath
if ($LASTEXITCODE -ne 0) { throw "Rust build failed" }
New-Item -ItemType Directory -Path (Join-Path $repoRoot "dist") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "target\release\sjturunningman.exe") -Destination $exePath

if (-not (Test-Path -LiteralPath $exePath)) {
    throw "Rust build did not create expected exe: $exePath"
}

Write-Host "Built $exePath"
Write-Host ("Size: {0:N2} MiB" -f ((Get-Item -LiteralPath $exePath).Length / 1MB))

# AI Agent for Minecraft - build all target versions
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$versions = @("1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10")
$modVersion = "1.2"

foreach ($v in $versions) {
    Write-Host ""
    Write-Host "##################################################"
    Write-Host "## Building $v"
    Write-Host "##################################################"
    & powershell -ExecutionPolicy Bypass -File "build.ps1" -McVersion $v -ModVersion $modVersion
    if ($LASTEXITCODE -ne 0) { throw "Build failed for $v" }
}

Write-Host ""
Write-Host "=== ALL BUILDS DONE ==="
Get-ChildItem "dist" -Filter "aafmc_*" | Select-Object Name, Length

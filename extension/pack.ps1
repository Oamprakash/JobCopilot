# Builds extension.zip (in the repository folder) for the Chrome Web Store, without the tests.
# Usage, from the repository folder:  .\extension\pack.ps1
$ErrorActionPreference = "Stop"
$src = $PSScriptRoot
$out = Join-Path (Split-Path $src -Parent) "extension.zip"
$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("ext-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $staging | Out-Null
Get-ChildItem $src -Exclude "test", "pack.ps1" | Copy-Item -Destination $staging -Recurse
if (Test-Path $out) { Remove-Item $out }
Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $out
Remove-Item $staging -Recurse -Force
Write-Host "Built $out"

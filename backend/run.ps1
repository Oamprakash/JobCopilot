# Starts the API on Windows: loads backend/.env (if present) into this process, then runs Gradle.
# Usage, from the backend folder:  .\run.ps1
# If PowerShell refuses to run scripts, run once:  Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    $loaded = @()
    foreach ($line in Get-Content $envFile) {
        # KEY=value lines; skips blanks and # comments. Surrounding quotes on the value are removed.
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            # Empty values are left unset so the API's defaults apply.
            if ($value -ne "") {
                [Environment]::SetEnvironmentVariable($name, $value, "Process")
                $loaded += $name
            }
        }
    }
    Write-Host "Loaded from .env: $($loaded -join ', ')"
} else {
    Write-Host "No .env found; using the API's defaults. Copy .env.example to .env to configure keys."
}

& .\gradlew.bat run
exit $LASTEXITCODE

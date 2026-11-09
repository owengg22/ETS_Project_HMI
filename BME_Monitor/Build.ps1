param([switch]$Install)
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "gradlew.bat") -p $PSScriptRoot --console=plain testDebugUnitTest assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Install) { & (Join-Path $PSScriptRoot "Install-Phone.ps1") }

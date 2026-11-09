param([string]$Serial = "")
$ErrorActionPreference = "Stop"
$properties = Join-Path $PSScriptRoot "local.properties"
if (!(Test-Path -LiteralPath $properties)) { throw "Buka project di Android Studio agar lokasi SDK tersimpan." }
$sdkLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
if (!$sdkLine) { throw "sdk.dir belum tersedia di local.properties." }
$sdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
$adb = Join-Path $sdk "platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path -LiteralPath $apk)) { throw "APK belum tersedia. Build assembleDebug terlebih dahulu." }
if (!(Test-Path -LiteralPath $adb)) { throw "ADB tidak ditemukan di SDK: $sdk" }
$devices = @(& $adb devices)
if ($LASTEXITCODE -ne 0) { throw "ADB gagal dijalankan." }
$ready = @($devices | ForEach-Object { if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] } })
if (!$Serial) {
    if ($ready.Count -ne 1) {
        $devices | Write-Host
        throw "Harus ada satu HP berstatus device. Aktifkan USB debugging dan setujui izin di HP; untuk beberapa HP gunakan -Serial."
    }
    $Serial = $ready[0]
}
if ($Serial -notin $ready) { throw "HP $Serial belum berstatus device." }
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Instalasi APK gagal." }
& $adb -s $Serial shell am start -n "id.ac.its.bmemonitor/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "APK terpasang, tetapi aplikasi belum berhasil dibuka." }
Write-Host "BME Monitor terpasang. Data live masuk otomatis. Gunakan Mulai rekam dan Stop untuk menyimpan sesi."

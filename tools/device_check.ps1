#requires -Version 5.1
# Uji asap adb untuk GeoPinPrivate. Tidak mengubah LSPosed dan tidak membuktikan hook bekerja.
[CmdletBinding()]
param(
    [string]$Serial = '', [string]$AdbPath = $env:ADB,
    [Alias('DryRun')][switch]$WhatIfOnly,
    [switch]$SkipLogcat, [switch]$ClearLogcat,
    [ValidateRange(5, 600)][int]$LogSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'
$Dist = Join-Path (Split-Path -Parent $PSScriptRoot) 'dist'
$PrivateApk = Join-Path $Dist 'GeoPinPrivate-0.1.0-debug.apk'
$ProbeApk = Join-Path $Dist 'GeoPinProbe-0.1.0-debug.apk'
$LogcatOut = Join-Path $Dist 'logcat-geopin.txt'
$ShotOut = Join-Path $Dist 'screenshot-controller.png'
$Controller = 'dev.geopin.privatepin/dev.geopin.app.ui.MainActivity'
$DeviceSerial = $Serial
$script:AdbExe = ''
$script:Passed = New-Object System.Collections.ArrayList
$script:Failed = New-Object System.Collections.ArrayList
$script:Manual = @(
    'LSPosed Manager > Modules: aktifkan modul Calibrator (dev.geopin.privatepin).',
    'LSPosed Manager > Scope: centang dev.geopin.probe. Controller cukup di-scope untuk indikator status.',
    'Calibrator > Paket yang diizinkan: isi dev.geopin.probe (default), simpan, lalu nyalakan titik uji.',
    'Paksa hentikan GeoPin Probe, buka lagi, beri izin lokasi, lalu tekan Mulai pantau.',
    'Periksa [GeoPin] di dist\logcat-geopin.txt; ada atau tidaknya baris bukan bukti nilai lokasi benar.'
)
function Write-Step([string]$T) { Write-Host ''; Write-Host $T }
function Add-Pass([string]$T) { [void]$script:Passed.Add($T); Write-Host "OK    $T" }
function Add-Fail([string]$T) { [void]$script:Failed.Add($T); Write-Host "GAGAL $T" }
function Find-Adb([string]$Requested) {
    if ($Requested -and (Test-Path -LiteralPath $Requested -PathType Leaf)) { return (Resolve-Path -LiteralPath $Requested).Path }
    $cmd = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { return $cmd.Source }
    $candidates = @("$env:ANDROID_HOME\platform-tools\adb.exe", "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe", "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", 'C:\Android\platform-tools\adb.exe', 'C:\platform-tools\adb.exe')
    foreach ($c in $candidates) { if ($c -and (Test-Path -LiteralPath $c)) { return (Resolve-Path -LiteralPath $c).Path } }
    return ''
}
function Invoke-Adb([string[]]$Arguments) {
    try { $raw = & $script:AdbExe @Arguments 2>&1; $code = $LASTEXITCODE }
    catch { return [pscustomobject]@{ ExitCode = 127; Output = $_.Exception.Message } }
    return [pscustomobject]@{ ExitCode = $code; Output = (($raw | ForEach-Object { [string]$_ }) -join "`n").Trim() }
}
function Show-Manual {
    Write-Host "`nLangkah manual yang tersisa:"
    for ($i = 0; $i -lt $script:Manual.Count; $i++) { Write-Host ("  {0}. {1}" -f ($i + 1), $script:Manual[$i]) }
}
function Show-Summary {
    Show-Manual
    Write-Host ''
    if ($WhatIfOnly) {
        Write-Host ("HASIL: DRY-RUN - {0} lolos, {1} gagal; 0 langkah perangkat dijalankan, bukan PASS." -f $script:Passed.Count, $script:Failed.Count)
    } elseif ($script:Failed.Count -gt 0) {
        Write-Host ("HASIL: FAIL - {0} gagal, {1} lolos. Hook tidak diklaim bekerja." -f $script:Failed.Count, $script:Passed.Count)
    } else {
        Write-Host ("HASIL: PASS - {0} langkah otomatis terverifikasi; hook TIDAK diverifikasi, logcat (bila direkam) hanya jejak." -f $script:Passed.Count)
    }
}
function Stop-Now([int]$Code) { Show-Summary; exit $Code }
if ($WhatIfOnly) { Write-Host 'MODE DRY-RUN: hanya memeriksa prasyarat dan mencetak rencana.' }
$script:AdbExe = Find-Adb $AdbPath
if (-not $script:AdbExe) {
    Write-Host 'ADB tidak ditemukan. Pasang Android Platform Tools, taruh adb di PATH, atau set $env:ADB.'
    Add-Fail 'adb tidak ditemukan'
    if (-not $WhatIfOnly) { Stop-Now 2 }
} else {
    Write-Step 'ADB'
    Write-Host "Path: $script:AdbExe"
    $v = Invoke-Adb @('version')
    if ($v.Output) { Write-Host $v.Output }
    if ($v.ExitCode -eq 0 -and $v.Output) { Add-Pass 'adb version terbaca' } else { Add-Fail "adb version gagal (exit $($v.ExitCode))" }
    $d = Invoke-Adb @('devices', '-l')
    if ($d.Output) { Write-Host $d.Output }
    $devices = @()
    foreach ($line in ($d.Output -split "`r?`n")) { if ($line -match '^(\S+)\s+(device|offline|unauthorized|no permissions)') { $devices += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] } } }
    $ready = @($devices | Where-Object { $_.State -eq 'device' -and (-not $DeviceSerial -or $_.Serial -eq $DeviceSerial) })
    if ($ready.Count -eq 0) {
        Write-Host 'Tidak ada perangkat adb dalam state device. Periksa USB debugging, izin RSA, kabel data, dan adb devices -l.'
        Add-Fail 'perangkat state device tidak ditemukan'
        if (-not $WhatIfOnly) { Stop-Now 2 }
    } else {
        if (-not $DeviceSerial) { $DeviceSerial = $ready[0].Serial }
        Add-Pass "perangkat adb siap: $DeviceSerial"
    }
}

Write-Step 'APK'
$apks = @(
    [pscustomobject]@{ Label = 'GeoPinPrivate'; Path = $PrivateApk },
    [pscustomobject]@{ Label = 'GeoPinProbe'; Path = $ProbeApk }
)
$missing = $false
foreach ($a in $apks) {
    if (-not (Test-Path -LiteralPath $a.Path -PathType Leaf)) { Write-Host "TIDAK ADA: $($a.Path)"; Add-Fail "APK $($a.Label) tidak ditemukan"; $missing = $true; continue }
    $h = (Get-FileHash -LiteralPath $a.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host ("{0}: {1} byte, SHA-256 {2}" -f $a.Label, (Get-Item -LiteralPath $a.Path).Length, $h)
    Add-Pass "APK $($a.Label) ada dan hash dibaca"
}
if ($missing -and -not $WhatIfOnly) { Stop-Now 2 }
if ($WhatIfOnly) {
    Write-Step 'Rencana'
    Write-Host 'Install kedua APK, launch controller, rekam logcat, lalu screenshot. Tidak ada aksi perangkat yang dijalankan.'
    Stop-Now 0
}

Write-Step 'Instalasi'
foreach ($a in $apks) {
    $r = Invoke-Adb @('-s', $DeviceSerial, 'install', '-r', $a.Path)
    if ($r.Output) { Write-Host $r.Output }
    if ($r.ExitCode -eq 0 -and $r.Output -match '(?m)^Success\s*$') { Add-Pass "instalasi $($a.Label): adb melaporkan Success" } else { Add-Fail "instalasi $($a.Label) gagal (exit $($r.ExitCode)); output adb di atas" }
}

Write-Step 'Controller'
$l = Invoke-Adb @('-s', $DeviceSerial, 'shell', 'am', 'start', '-W', '-n', $Controller)
if ($l.Output) { Write-Host $l.Output }
Start-Sleep -Seconds 2
$pids = (Invoke-Adb @('-s', $DeviceSerial, 'shell', 'pidof', 'dev.geopin.privatepin')).Output
if ($l.ExitCode -eq 0 -and $l.Output -notmatch '(?m)^Error' -and $pids -match '\d+') { Add-Pass "controller berjalan (pid $pids)" } else { Add-Fail "controller tidak berjalan setelah am start (exit $($l.ExitCode))" }
Show-Manual

if ($SkipLogcat) {
    Write-Host ''
    Write-Host 'Logcat dilewati (-SkipLogcat); file logcat tidak dibuat.'
} else {
    Write-Step 'Logcat'
    if ($ClearLogcat) {
        $c = Invoke-Adb @('-s', $DeviceSerial, 'logcat', '-c')
        if ($c.ExitCode -eq 0) { Add-Pass 'logcat dibersihkan' } else { Add-Fail 'logcat -c gagal' }
    }
    $to = [System.IO.Path]::GetTempFileName()
    Write-Host ("Merekam {0} detik; lakukan langkah manual di perangkat sekarang." -f $LogSeconds)
    $proc = $null
    $proc = Start-Process -FilePath $script:AdbExe -ArgumentList @('-s', $DeviceSerial, 'logcat', '-v', 'time') -NoNewWindow -PassThru -RedirectStandardOutput $to -ErrorAction SilentlyContinue
    if (-not $proc) {
        Remove-Item -LiteralPath $to -Force -ErrorAction SilentlyContinue
        Add-Fail ("logcat tidak dapat dijalankan: {0}" -f $Error[0].Exception.Message)
    } else {
        $done = $proc.WaitForExit($LogSeconds * 1000)
        if (-not $done) { try { $proc.Kill() } catch { }; try { [void]$proc.WaitForExit(3000) } catch { } }
        Start-Sleep -Milliseconds 300
        $raw = @(Get-Content -LiteralPath $to -Encoding UTF8 -ErrorAction SilentlyContinue)
        Remove-Item -LiteralPath $to -Force -ErrorAction SilentlyContinue
        $geo = @($raw | Where-Object { ([string]$_).Contains('GeoPin') })
        if ($done -and $null -ne $proc.ExitCode -and $proc.ExitCode -ne 0 -and $geo.Count -eq 0) {
            Add-Fail "logcat keluar lebih awal (exit $($proc.ExitCode))"
        } else {
            $head = @('# GeoPin logcat', ('# ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')), ('# serial ' + $DeviceSerial), ('# jendela ' + $LogSeconds + ' detik'), '# bukti mentah; bukan bukti hook berfungsi.')
            Set-Content -LiteralPath $LogcatOut -Value ($head + $geo) -Encoding UTF8 -ErrorAction SilentlyContinue
            if ($?) {
                Add-Pass "logcat ditulis ke $LogcatOut ($($geo.Count) baris cocok)"
                if ($geo.Count -eq 0) { Write-Host 'Tidak ada baris GeoPin; ini belum membuktikan apa pun.' }
            } else {
                Add-Fail 'gagal menulis logcat'
            }
        }
    }
}

Write-Step 'Screenshot controller'
$null = Invoke-Adb @('-s', $DeviceSerial, 'shell', 'am', 'start', '-W', '-n', $Controller)
Start-Sleep -Seconds 2
if ($DeviceSerial -notmatch '^[A-Za-z0-9._:-]+$') {
    Add-Fail 'serial perangkat tidak aman untuk perintah screenshot'
} else {
    if (Test-Path -LiteralPath $ShotOut) { Remove-Item -LiteralPath $ShotOut -Force -ErrorAction SilentlyContinue }
    & $env:ComSpec /d /c ('"{0}" -s {1} exec-out screencap -p > "{2}"' -f $script:AdbExe, $DeviceSerial, $ShotOut)
    $code = $LASTEXITCODE
    $ok = $false
    if (Test-Path -LiteralPath $ShotOut) {
        try { $b = [System.IO.File]::ReadAllBytes($ShotOut); $ok = ($b.Length -gt 8 -and $b[0] -eq 137 -and $b[1] -eq 80 -and $b[2] -eq 78 -and $b[3] -eq 71) } catch { $ok = $false }
    }
    if ($ok) { Add-Pass "screenshot PNG tersimpan ($((Get-Item -LiteralPath $ShotOut).Length) byte); isi tidak diverifikasi" } else { Add-Fail "screenshot gagal atau bukan PNG (exit $code)" }
}

Show-Summary
if ($script:Failed.Count -gt 0) { exit 1 }
exit 0

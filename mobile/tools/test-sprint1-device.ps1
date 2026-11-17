param([string]$DeviceSerial='emulator-5554',[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$mobileRoot=Split-Path -Parent $PSScriptRoot
$workspace=Split-Path -Parent $mobileRoot
$backendRoot=Join-Path $workspace 'backend'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if(-not (Test-Path -LiteralPath $adb)){throw 'Android SDK platform-tools required.'}
Invoke-RestMethod 'http://127.0.0.1:8001/api/v1/health' | Out-Null
Invoke-WebRequest 'http://localhost:4200' -UseBasicParsing | Out-Null
if(Get-NetTCPConnection -LocalPort 8123 -State Listen -ErrorAction SilentlyContinue){throw 'Port 8123 already in use; do not reuse another fixture bridge.'}
New-Item -ItemType Directory -Force (Join-Path $mobileRoot 'build') | Out-Null
$bridge=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @(('"'+(Join-Path $backendRoot 'tools/mobile-recipe.cjs')+'"')) -WorkingDirectory $workspace -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backendRoot 'target/mobile-fixture.stdout.log') -RedirectStandardError (Join-Path $backendRoot 'target/mobile-fixture.stderr.log')
try {
    $ready=$false
    for($attempt=0;$attempt -lt 30;$attempt++){
        try { $ready=(Invoke-RestMethod 'http://127.0.0.1:8123/health').ready; if($ready){break} } catch { }
        if($bridge.HasExited){throw 'Fixture bridge failed; see backend/target/mobile-fixture.stderr.log.'}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw 'Fixture bridge did not start.'}
    Push-Location $mobileRoot
    try {
        if(-not $SkipBuild){
            & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/' --console=plain
            if($LASTEXITCODE -ne 0){throw 'Android build failed.'}
        }
        & $adb -s $DeviceSerial reverse tcp:8001 tcp:8001
        & $adb -s $DeviceSerial reverse tcp:8123 tcp:8123
        & $adb -s $DeviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
        if($LASTEXITCODE -ne 0){throw 'Application installation failed.'}
        & $adb -s $DeviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if($LASTEXITCODE -ne 0){throw 'Test installation failed.'}
        $output=& $adb -s $DeviceSerial shell am instrument -w -e fastcolocLive true -e class com.iset.fastcolloc.Sprint1DeviceTest,com.iset.fastcolloc.AndroidSecurityTest,com.iset.fastcolloc.OAuthSafetyTest com.iset.fastcolloc.test/androidx.test.runner.AndroidJUnitRunner
        $output | Set-Content -LiteralPath build/device-recipe.log -Encoding UTF8
        $output | Write-Output
        if(($output -join "`n") -notmatch 'OK \(5 tests\)'){throw 'Device recipe failed; inspect build/device-recipe.log.'}
        $capture=Start-Process -FilePath $adb -ArgumentList @('-s',$DeviceSerial,'pull','/sdcard/Android/data/com.iset.fastcolloc/files/sprint1-profile.png','build/design-preview/sprint1-profile.png') -WorkingDirectory $mobileRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $mobileRoot 'build/capture.stdout.log') -RedirectStandardError (Join-Path $mobileRoot 'build/capture.stderr.log')
        if($capture.ExitCode -ne 0){throw 'Device screenshot copy failed.'}
    } finally { Pop-Location }
} finally {
    try { Invoke-RestMethod 'http://127.0.0.1:8123/finish' | Out-Null } catch { Write-Warning 'Fixture cleanup was not confirmed; inspect the fixture bridge before stopping it.' }
    & $adb -s $DeviceSerial reverse --remove tcp:8123
}

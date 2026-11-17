param([string]$DeviceSerial='emulator-5554',[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$mobileRoot=Split-Path -Parent $PSScriptRoot
$workspace=Split-Path -Parent $mobileRoot
$backendRoot=Join-Path $workspace 'backend'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
Invoke-RestMethod 'http://127.0.0.1:8001/api/v1/health' | Out-Null
if(Get-NetTCPConnection -LocalPort 8124 -State Listen -ErrorAction SilentlyContinue){throw 'Port 8124 is already in use.'}
$bridge=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @(('"'+(Join-Path $backendRoot 'tools/sprint2-mobile-recipe.cjs')+'"')) -WorkingDirectory $workspace -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $backendRoot 'target/sprint2-mobile-bridge.stdout.log') -RedirectStandardError (Join-Path $backendRoot 'target/sprint2-mobile-bridge.stderr.log')
try {
    $ready=$false
    for($attempt=0;$attempt -lt 180;$attempt++){
        try{$ready=(Invoke-RestMethod 'http://127.0.0.1:8124/health').ready;if($ready){break}}catch{}
        if($bridge.HasExited){throw 'Sprint 2 fixture bridge failed; inspect its stderr log.'}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw 'Sprint 2 fixture bridge did not become ready.'}
    Push-Location $mobileRoot
    try {
        if(-not $SkipBuild){& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PfastcolocApiUrl=http://127.0.0.1:8001/api/v1/' --console=plain;if($LASTEXITCODE -ne 0){throw 'Android build failed.'}}
        & $adb -s $DeviceSerial reverse tcp:8001 tcp:8001
        & $adb -s $DeviceSerial reverse tcp:8124 tcp:8124
        & $adb -s $DeviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
        if($LASTEXITCODE -ne 0){throw 'App installation failed.'}
        & $adb -s $DeviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if($LASTEXITCODE -ne 0){throw 'Test installation failed.'}
        $output=& $adb -s $DeviceSerial shell am instrument -w -e fastcolocLive true -e class com.iset.fastcolloc.Sprint2DeviceTest com.iset.fastcolloc.test/androidx.test.runner.AndroidJUnitRunner
        $output | Set-Content -LiteralPath build/sprint2-device-recipe.log -Encoding UTF8
        $output | Write-Output
        if(($output -join "`n") -notmatch 'OK \(1 test\)'){throw 'Sprint 2 device recipe failed.'}
    }finally{Pop-Location}
}finally{
    try{Invoke-RestMethod 'http://127.0.0.1:8124/finish' | Out-Null}catch{Write-Warning 'Sprint 2 fixture cleanup not confirmed; inspect bridge before stopping it.'}
    & $adb -s $DeviceSerial reverse --remove tcp:8124
}

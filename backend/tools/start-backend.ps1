param([switch]$Background)
$ErrorActionPreference='Stop'
$backend=Split-Path -Parent $PSScriptRoot
Push-Location $backend
try {
    $jar=Join-Path $backend 'target/fastcoloc-backend-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Compile first: mvn.cmd -B -ntp verify' }
    $runFolder=Join-Path $backend 'target/run'
    $runJar=Join-Path $runFolder 'fastcoloc-backend.jar'
    $pidFile=Join-Path $runFolder 'server.pid'
    if (Test-Path -LiteralPath $pidFile) {
        $serverProcessId=[int](Get-Content -LiteralPath $pidFile)
        $existing=Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue
        if ($existing) { throw 'A recorded process still exists. Stop the recorded FastColoc process before restarting.' }
    }
    New-Item -ItemType Directory -Force -Path $runFolder | Out-Null
    # Run a copy so Windows does not lock Maven's build artifact during verify/repackage.
    Copy-Item -LiteralPath $jar -Destination $runJar -Force
    if ($Background) {
        $process=Start-Process -FilePath 'java' -ArgumentList @('-jar',('"'+$runJar+'"')) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $backend 'target/server.stdout.log') -RedirectStandardError (Join-Path $backend 'target/server.stderr.log') -PassThru
        [IO.File]::WriteAllText($pidFile,[string]$process.Id)
        Write-Output ('FastColoc started, PID '+$process.Id+'; logs: target/server.stdout.log')
    } else { & java -jar $runJar }
} finally { Pop-Location }

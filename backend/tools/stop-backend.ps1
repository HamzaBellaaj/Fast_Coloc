$ErrorActionPreference='Stop'
$backend=Split-Path -Parent $PSScriptRoot
$pidFile=Join-Path $backend 'target/run/server.pid'
$runJar=Join-Path $backend 'target/run/fastcoloc-backend.jar'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Output 'No recorded FastColoc process.'; return }
$serverProcessId=[int](Get-Content -LiteralPath $pidFile)
$process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$serverProcessId)
if ($process) {
    if ($process.Name -ne 'java.exe' -or $process.CommandLine.IndexOf($runJar,[StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'Recorded PID does not match this FastColoc executable; refusing to stop another process.'
    }
    Stop-Process -Id $serverProcessId
    Write-Output ('Stopped FastColoc PID '+$serverProcessId)
}
Remove-Item -LiteralPath $pidFile

$ErrorActionPreference='Stop'
$backend=Split-Path -Parent $PSScriptRoot
Push-Location $backend
$environmentFile=Join-Path $backend 'target/newman.local.postman_environment.json'
try {
    $cfg=@{}
    Get-Content -Encoding UTF8 '.env.local' | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { $cfg[$matches[1]]=$matches[2].Trim() } }
    $environment=Get-Content -Raw -Encoding UTF8 'postman/FastColoc-local.postman_environment.example.json' | ConvertFrom-Json
    foreach ($entry in $environment.values) {
        if ($entry.key -eq 'email') { $entry.value=$cfg.FASTCOLOC_TEST_EMAIL }
        if ($entry.key -eq 'password') { $entry.value=$cfg.FASTCOLOC_TEST_EMAIL_PASSWORD }
    }
    [IO.File]::WriteAllText($environmentFile,($environment | ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
    & npx.cmd --yes newman@6.2.1 run 'postman/FastColoc-Sprint1.postman_collection.json' -e $environmentFile --folder 'Smoke' --reporters cli --reporter-cli-no-console
    $runExit=$LASTEXITCODE
} finally {
    if (Test-Path -LiteralPath $environmentFile) { Remove-Item -LiteralPath $environmentFile }
    Pop-Location
}
exit $runExit

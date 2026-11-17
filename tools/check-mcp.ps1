$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$extensionRoot = Join-Path $env:USERPROFILE '.vscode\extensions'
$binaries = @(Get-ChildItem -LiteralPath $extensionRoot -Directory -Filter 'openai.chatgpt*' |
    Sort-Object LastWriteTime -Descending | ForEach-Object {
        $binary = Join-Path $_.FullName 'bin\windows-x86_64\codex.exe'
        if (Test-Path -LiteralPath $binary) { $binary }
    })
if (-not $binaries.Count) { throw 'Codex executable from the VS Code extension was not found.' }
Push-Location $projectRoot
try {
    & $binaries[0] mcp list
    if ($LASTEXITCODE -ne 0) { throw 'MCP inspection failed.' }
} finally { Pop-Location }

<#
.SYNOPSIS
  Startet den Bot: erst Main pullen, dann bauen, dann das Jar starten.
.EXAMPLE
  .\scripts\start-bot.ps1
  $env:SLOTH_SKIP_UPDATE = '1'; .\scripts\start-bot.ps1   # ohne Pull starten
#>
[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$BotArgs)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if ($env:SLOTH_SKIP_UPDATE -eq '1') {
    Write-Host '>> Update uebersprungen (SLOTH_SKIP_UPDATE=1).'
} else {
    # Fehlschlag (offline, Konflikt, ...) darf den Start nicht verhindern.
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'update-main.ps1')
    if ($LASTEXITCODE -ne 0) {
        Write-Host '!! Update fehlgeschlagen - starte mit dem aktuellen lokalen Stand.'
    }
}

Write-Host '>> Baue Jar...'
& .\gradlew.bat --quiet shadowJar
if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen (Exit $LASTEXITCODE)." }

$jar = Get-ChildItem build\libs\*.jar |
       Sort-Object { $_.Name -like '*-all.jar' }, LastWriteTime -Descending |
       Select-Object -First 1
if (-not $jar) { throw 'Kein Jar in build\libs gefunden.' }

Write-Host ">> Starte $($jar.FullName)"
& java -jar $jar.FullName @BotArgs
exit $LASTEXITCODE

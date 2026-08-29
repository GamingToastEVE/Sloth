<#
.SYNOPSIS
  Holt die neueste Version des Haupt-Branches.
.EXAMPLE
  .\scripts\update-main.ps1              # Main in den aktuellen Branch mergen
  .\scripts\update-main.ps1 -Rebase      # statt merge rebasen
  .\scripts\update-main.ps1 -Checkout    # auf Main wechseln und dort pullen
  .\scripts\update-main.ps1 -Remote origin -Main main
#>
[CmdletBinding()]
param(
    [switch]$Rebase,
    [switch]$Checkout,
    [string]$Remote,
    [string]$Main
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & git @Args
    if ($LASTEXITCODE -ne 0) { throw "git $($Args -join ' ') ist fehlgeschlagen (Exit $LASTEXITCODE)." }
}

# Remote bestimmen: Parameter > origin > erster vorhandener
if (-not $Remote) {
    $remotes = @(& git remote)
    if ($remotes -contains 'origin') { $Remote = 'origin' }
    elseif ($remotes.Count -gt 0)    { $Remote = $remotes[0] }
    else { throw 'Kein Git-Remote konfiguriert.' }
}

# Haupt-Branch bestimmen: Parameter > HEAD des Remotes > main > master
if (-not $Main) {
    $headLine = (& git remote show $Remote) | Select-String 'HEAD branch:' | Select-Object -First 1
    if ($headLine) { $Main = ($headLine -split 'HEAD branch:')[1].Trim() }
}
if (-not $Main) {
    & git ls-remote --exit-code --heads $Remote main *> $null
    if ($LASTEXITCODE -eq 0) { $Main = 'main' } else { $Main = 'master' }
}

$current = (& git rev-parse --abbrev-ref HEAD).Trim()
Write-Host ">> Remote: $Remote | Main: $Main | Aktueller Branch: $current"

# Nur getrackte Aenderungen blockieren; untracked Dateien (Logs, DBs, ...) sind egal.
$dirty = & git status --porcelain --untracked-files=no
if ($dirty) {
    Write-Host '!! Es gibt uncommittete Aenderungen. Bitte committen oder stashen.'
    & git status --short --untracked-files=no
    exit 1
}

Write-Host ">> git fetch $Remote $Main"
Invoke-Git fetch $Remote $Main --prune

if ($Checkout) {
    Invoke-Git checkout $Main
    Invoke-Git merge --ff-only "$Remote/$Main"
} elseif ($Rebase) {
    Invoke-Git rebase "$Remote/$Main"
} else {
    Invoke-Git merge --no-edit "$Remote/$Main"
}

Write-Host ">> Fertig. HEAD: $(& git log -1 --oneline)"

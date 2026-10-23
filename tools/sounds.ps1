# Push interface sound packs to the launcher on the TV, then pick one there from the Sound pill.
# A pack is a folder of short sounds named by event: navigation, bump, confirm, launch, back, open,
# close, on (.wav, .ogg or .mp3; missing ones stay silent). No sound ships in the APK.
#
# Usage: tools\sounds.ps1 -TvHost 192.168.1.50 <folder of pack folders>    e.g. packs\ps2\navigation.wav
#        tools\sounds.ps1 -TvHost 192.168.1.50 -Reset                      removes every pack from the TV
param(
    [Parameter(Position = 0)][string]$Packs,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if ($Packs) { $Packs = (Resolve-Path $Packs).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$dir = '/sdcard/Android/data/com.gathin.tvhome/files/sounds'

if ($Reset) {
    & $py -I tv.py "rm -rf $dir; echo removed"
    exit 0
}
if (-not $Packs) { throw 'usage: sounds.ps1 <folder of pack folders> | -Reset' }
$events = 'navigation', 'bump', 'confirm', 'launch', 'back', 'open', 'close', 'on'
foreach ($pack in Get-ChildItem $Packs -Directory) {
    # Folder names go into a shell command on the TV: letters, digits, - and _ only.
    if ($pack.Name -notmatch '^[A-Za-z0-9_-]+$') { Write-Host "Skipped $($pack.Name): use letters, digits, - and _"; continue }
    $files = Get-ChildItem $pack.FullName -File | Where-Object {
        $_.Extension -in '.wav', '.ogg', '.mp3' -and $events -contains $_.BaseName
    }
    if (-not $files) { continue }
    & $py -I tv.py "rm -rf $dir/$($pack.Name); mkdir -p $dir/$($pack.Name)" | Out-Null
    foreach ($f in $files) { & $py -I b64push.py $f.FullName "$dir/$($pack.Name)/$($f.Name)" | Select-Object -Last 1 }
    Write-Host "Pack $($pack.Name): $($files.Count) sounds"
}
Write-Host 'Done. On the TV: Home, the Sound pill (speaker icon), pick a pack. Undo with: tools\sounds.ps1 -Reset'

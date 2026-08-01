# Grabs a screenshot from the attached device and writes a downscaled copy next to it.
param([Parameter(Mandatory = $true)][string]$Name, [int]$Width = 380)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dir = Join-Path $PSScriptRoot '..\build\shots'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$png = Join-Path $dir "$Name.png"
& $adb shell screencap -p /sdcard/shot.png
& $adb pull /sdcard/shot.png $png 2>&1 | Out-Null
& "$PSScriptRoot\shrink-shot.ps1" -Path $png -Width $Width

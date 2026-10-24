param([switch]$NoBrowser)
& (Join-Path $PSScriptRoot 'start-prototype.ps1') -NoBrowser:$NoBrowser

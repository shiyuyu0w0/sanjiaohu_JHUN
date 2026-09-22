param(
    [string]$Manifest = "$PSScriptRoot/../updates/stable/version.json",
    [string]$Apk = "$PSScriptRoot/../Sanjiaohu-1.1.2.apk",
    [switch]$Offline,
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$BuildTools = '36.1.0'
)
. "$PSScriptRoot/update-common.ps1"
$facts = Get-UpdateApk $Apk $Java $Sdk $BuildTools
$data = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-UpdateManifest $data $facts
Write-Output "Local package, version, signature, size and SHA-256 verified: $($facts.VersionName) ($($facts.VersionCode))"
if (!$Offline) { Test-UpdateLinks $data }

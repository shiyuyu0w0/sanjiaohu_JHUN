param(
    [string]$Apk = "$PSScriptRoot/../Sanjiaohu-1.1.3.apk",
    [Parameter(Mandatory=$true)][long]$ManifestRevision,
    [Parameter(Mandatory=$true)][string]$NotesFile,
    [string]$Output = "$PSScriptRoot/../updates/stable/version.json",
    [switch]$Enable,
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$BuildTools = '36.1.0'
)
. "$PSScriptRoot/update-common.ps1"
$facts = Get-UpdateApk $Apk $Java $Sdk $BuildTools
$target = [IO.Path]::GetFullPath($Output)
$rootPrefix = [IO.Path]::GetFullPath($script:UpdateRoot).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
if (!$target.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Manifest output must stay in this workspace.' }
if (Test-Path -LiteralPath $target) {
    $old = Get-Content -LiteralPath $target -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($ManifestRevision -le $old.manifestRevision) { throw 'Every manifest change must increase manifestRevision, including disabling a release.' }
}
$notes = @(Get-Content -LiteralPath $NotesFile -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
$url = $script:UpdateRepo + 'releases/download/v' + $facts.VersionName + '/Sanjiaohu-' + $facts.VersionName + '.apk'
$manifest = [pscustomobject][ordered]@{
    schemaVersion=1; manifestRevision=$ManifestRevision; enabled=[bool]$Enable; channel='stable'; packageName='cn.jhun.sanjiaohu'
    versionCode=$facts.VersionCode; versionName=$facts.VersionName; minSdk=$facts.MinSdk; releaseNotes=$notes
    releasePage=($script:UpdateRepo + 'releases/tag/v' + $facts.VersionName)
    apk=[pscustomobject][ordered]@{url=$url; sizeBytes=$facts.Size; sha256=$facts.Sha256; mirrors=@([pscustomobject]@{id='ghproxy'; name='Accelerated'; url=('https://ghproxy.net/' + $url)})}
}
Assert-UpdateManifest $manifest $facts
# Enabling is the last step: both published routes must return these exact bytes.
if ($Enable) { Test-UpdateLinks $manifest }
$parent = Split-Path $target -Parent
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$temporary = $target + '.tmp'
try {
    [IO.File]::WriteAllText($temporary, ($manifest | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $temporary -Destination $target -Force
} finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force } }
Write-Output "Prepared manifest: $target (enabled=$([bool]$Enable))"

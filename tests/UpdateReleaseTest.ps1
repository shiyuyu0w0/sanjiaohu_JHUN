param(
    [string]$Apk = "$PSScriptRoot/../Sanjiaohu-1.1.2.apk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$BuildTools = '36.1.0'
)
. "$PSScriptRoot/../scripts/update-common.ps1"
$facts = Get-UpdateApk $Apk $Java $Sdk $BuildTools
$testDirectory = Join-Path $script:UpdateRoot ('build/update-release-tests/' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $testDirectory | Out-Null
$manifestFile = Join-Path $testDirectory 'version.json'
$notesFile = Join-Path $testDirectory 'notes.txt'
[IO.File]::WriteAllText($notesFile, 'Test release notes', (New-Object Text.UTF8Encoding($false)))
& "$PSScriptRoot/../scripts/prepare-update.ps1" -Apk $Apk -ManifestRevision 1 -NotesFile $notesFile -Output $manifestFile -Java $Java -Sdk $Sdk -BuildTools $BuildTools
$raw = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8
$manifest = $raw | ConvertFrom-Json
Assert-UpdateManifest $manifest $facts
if ($manifest.enabled) { throw 'Unpublished manifest must stay disabled.' }
$script:releaseChecks = 2
function Reject([scriptblock]$Work) {
    $rejected = $false
    try { & $Work } catch { $rejected = $true }
    if (!$rejected) { throw 'Expected invalid release to be rejected.' }
    $script:releaseChecks++
}
Reject { & "$PSScriptRoot/../scripts/prepare-update.ps1" -Apk $Apk -ManifestRevision 1 -NotesFile $notesFile -Output $manifestFile -Java $Java -Sdk $Sdk -BuildTools $BuildTools }
if ((Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8) -cne $raw) { throw 'Failed generation modified existing manifest.' }
$script:releaseChecks++
Reject { $m = $raw | ConvertFrom-Json; $m.apk.sha256 = ('0' * 64); Assert-UpdateManifest $m $facts }
Reject { $m = $raw | ConvertFrom-Json; $m.apk.sizeBytes++; Assert-UpdateManifest $m $facts }
Reject { $m = $raw | ConvertFrom-Json; $m.versionCode++; Assert-UpdateManifest $m $facts }
Reject { $m = $raw | ConvertFrom-Json; $m.packageName = 'invalid.package'; Assert-UpdateManifest $m $facts }
Reject { $m = $raw | ConvertFrom-Json; $m.manifestRevision = '2'; Assert-UpdateManifest $m $facts }
Reject { $m = $raw | ConvertFrom-Json; $m.apk.mirrors[0].url = 'https://example.com/wrong.apk'; Assert-UpdateManifest $m $facts }
Write-Output "Release script checks: $script:releaseChecks passed"

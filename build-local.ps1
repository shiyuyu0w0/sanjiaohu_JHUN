param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Platform = 'android-36.1',
    [string]$BuildTools = '36.1.0',
    [string]$Work = "$PSScriptRoot/build/android-build",
    [string]$Apk = "$PSScriptRoot/Sanjiaohu-1.1.4.apk"
)
$ErrorActionPreference = 'Stop'
# Relaxed only around native calls: Windows PowerShell turns a native tool's stderr into a
# terminating error while this is 'Stop', and javac writes its "deprecated API" note to stderr on
# every run. Restore stays inline (never in a function): calling a function resets $LASTEXITCODE.
$script:nativePreference = $null
function Relax-Native { $script:nativePreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue' }
function Restore-Native { if ($null -ne $script:nativePreference) { $ErrorActionPreference = $script:nativePreference; $script:nativePreference = $null } }
$tool = Join-Path $Sdk "build-tools/$BuildTools"
$android = Join-Path $Sdk "platforms/$Platform/android.jar"
$app = Join-Path $PSScriptRoot 'app/src/main'
$resolvedWork = [System.IO.Path]::GetFullPath($Work)
foreach ($part in @('classes', 'generated', 'dex')) {
    $cleanPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedWork $part))
    if (!$cleanPath.StartsWith($resolvedWork.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup target outside work directory' }
    if (Test-Path -LiteralPath $cleanPath) { Remove-Item -LiteralPath $cleanPath -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $Work,"$Work/classes","$Work/generated","$Work/dex" | Out-Null
function Check { if ($LASTEXITCODE -ne 0) { throw "Build step failed: $LASTEXITCODE" } }
& "$tool/aapt2.exe" compile --dir "$app/res" -o "$Work/resources.zip"
Check
$manifestText = Get-Content -LiteralPath "$app/AndroidManifest.xml" -Raw -Encoding UTF8
# UTF-8 without a BOM is mandatory here: aapt2 rejects a BOM as "not well-formed", and
# Get-Content without -Encoding would decode the Chinese label as the ANSI code page.
# Keep this file pure ASCII: Windows PowerShell 5.1 reads a BOM-less .ps1 as ANSI.
$manifestWithPackage = $manifestText.Replace('<manifest ', '<manifest package="cn.jhun.sanjiaohu" ')
[System.IO.File]::WriteAllText("$Work/AndroidManifest.xml", $manifestWithPackage, (New-Object System.Text.UTF8Encoding($false)))
& "$tool/aapt2.exe" link -o "$Work/unsigned.apk" -I $android --manifest "$Work/AndroidManifest.xml" -A "$app/assets" --java "$Work/generated" "$Work/resources.zip"
Check
$sources = @(Get-ChildItem -LiteralPath "$app/java","$Work/generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Relax-Native
& "$Java/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$android;$tool/core-lambda-stubs.jar" -d "$Work/classes" @sources
Restore-Native
Check
& "$Java/bin/jar.exe" cf "$Work/classes.jar" -C "$Work/classes" .
Check
& "$Java/bin/java.exe" -cp "$tool/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 26 --output "$Work/dex" "$Work/classes.jar"
Check
& "$Java/bin/jar.exe" uf "$Work/unsigned.apk" -C "$Work/dex" classes.dex
Check
& "$tool/zipalign.exe" -f -p 4 "$Work/unsigned.apk" "$Work/aligned.apk"
Check
# The official signing key wins: swapping it silently produces an APK that cannot be
# installed over the released one, and the user's local data would be lost.
$signingKeystore = Join-Path $PSScriptRoot 'signing/development.keystore'
$workKeystore = Join-Path $Work 'development.keystore'
if (Test-Path -LiteralPath $signingKeystore) { $keystore = $signingKeystore }
elseif (Test-Path -LiteralPath $workKeystore) { $keystore = $workKeystore }
else {
    # Never generate a new key silently; fail loud instead. See signing/README.md.
    throw 'Official signing key signing/development.keystore not found. Restore the original key before building.'
}
if ($keystore -ne $signingKeystore) { Write-Warning "Signing with $keystore instead of the official key in signing/." }
# Print the fingerprint signing/README.md records, so a swapped key is noticed immediately.
$script:certLine = @(& "$Java/bin/keytool.exe" -list -v -keystore $keystore -storepass android -alias androiddebugkey 2>&1) -match 'SHA256:' | Select-Object -First 1
Write-Output "Signing key: $keystore"
Write-Output "Cert: $script:certLine"
& "$Java/bin/java.exe" -jar "$tool/lib/apksigner.jar" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $Apk "$Work/aligned.apk"
Check
& "$Java/bin/java.exe" -jar "$tool/lib/apksigner.jar" verify --verbose $Apk
Check
Get-FileHash -LiteralPath $Apk -Algorithm SHA256

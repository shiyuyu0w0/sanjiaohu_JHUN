# Re-sign v1.1.3 with the OFFICIAL signing key.
#
# Usage:
#   1. Put the official key at signing/development.keystore
#   2. Run:  powershell -NoProfile -ExecutionPolicy Bypass -File .\resign-release.ps1
#
# The script verifies the certificate fingerprint FIRST and only proceeds when it
# equals the official one recorded in README v1.1.0. On mismatch it aborts without
# producing an APK, so a wrong key can never yield a publishable artifact.
#
# NOTE: this file must stay pure ASCII. Windows PowerShell 5.1 reads a BOM-less
# .ps1 as the ANSI code page, which corrupts non-ASCII text and causes parse
# errors. Same reason build-local.ps1 keeps its own content ASCII.

param(
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$BuildTools = '36.1.0',
    [string]$Platform = 'android-36.1',
    [string]$Keystore = "$PSScriptRoot/signing/development.keystore",
    [string]$StorePass = 'android',
    [string]$KeyPass = 'android',
    [string]$Alias = 'androiddebugkey',
    [string]$Apk = "$PSScriptRoot/Sanjiaohu-1.1.3.apk",
    # Official certificate fingerprint, recorded in README v1.1.0.
    [string]$Expected = '2D:5C:4C:7A:B3:F5:E8:29:DF:B2:27:3F:33:3A:2A:F9:87:E5:12:DC:27:0F:BE:03:34:76:38:32:9C:DE:F2:2A'
)
$ErrorActionPreference = 'Stop'

$tool = Join-Path $Sdk "build-tools/$BuildTools"
$android = Join-Path $Sdk "platforms/$Platform/android.jar"
$signer = Join-Path $tool 'lib/apksigner.jar'

Write-Host '== 1. locate keystore ==' -ForegroundColor Cyan
if (!(Test-Path -LiteralPath $Keystore)) {
    throw "Official keystore not found: $Keystore`nPut the backed-up development.keystore into signing/ and re-run."
}
Write-Host "  keystore: $Keystore"

Write-Host '== 2. verify fingerprint (abort on mismatch) ==' -ForegroundColor Cyan
$certRaw = & "$Java/bin/keytool.exe" -list -v -keystore $Keystore -storepass $StorePass -alias $Alias 2>&1 | Out-String
$m = [regex]::Match($certRaw, 'SHA256:\s*([0-9A-Fa-f:]{95})')
if (!$m.Success) { throw 'Cannot read certificate fingerprint; check -Alias / -StorePass.' }
$actual = $m.Groups[1].Value.ToUpper()
Write-Host "  actual  : $actual"
Write-Host "  expected: $($Expected.ToUpper())"
if ($actual -ne $Expected.ToUpper()) {
    throw @"
Fingerprint mismatch - aborted, no APK produced.

  actual  : $actual
  expected: $Expected

This is NOT the official key. Signing with it yields an APK that cannot be
installed over the released build. Restore the official key. If it is truly
lost, decide in the project to switch signing keys and tell users to reinstall.
"@
}
Write-Host '  fingerprint matches' -ForegroundColor Green

Write-Host '== 3. build (resources -> java -> dex -> align) ==' -ForegroundColor Cyan
$Work = Join-Path $PSScriptRoot 'build/android-build'
foreach ($part in @('classes', 'generated', 'dex')) {
    $clean = Join-Path $Work $part
    if (Test-Path -LiteralPath $clean) { Remove-Item -LiteralPath $clean -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $Work, "$Work/classes", "$Work/generated", "$Work/dex" | Out-Null
function Check { if ($LASTEXITCODE -ne 0) { throw "step failed: $LASTEXITCODE" } }

# aapt2 cannot open an absolute path containing non-ASCII characters, so use
# relative paths and run from the project root.
Push-Location $PSScriptRoot
try {
    & "$tool/aapt2.exe" compile --dir "app/src/main/res" -o "$Work/resources.zip"; Check
    $mt = Get-Content -LiteralPath "app/src/main/AndroidManifest.xml" -Raw -Encoding UTF8
    # No BOM: aapt2 rejects a BOM as "not well-formed".
    # $Work is already absolute; join with the manifest name only.
    [IO.File]::WriteAllText((Join-Path $Work 'AndroidManifest.xml'),
        $mt.Replace('<manifest ', '<manifest package="cn.jhun.sanjiaohu" '),
        (New-Object Text.UTF8Encoding($false)))
    & "$tool/aapt2.exe" link -o "$Work/unsigned.apk" -I $android `
        --manifest "$Work/AndroidManifest.xml" -A "app/src/main/assets" `
        --java "$Work/generated" "$Work/resources.zip"; Check

    # Response file: PowerShell mangles -D... style args and spaced paths.
    $q = '"'
    $srcs = @()
    $srcs += Get-ChildItem "app/src/main/java" -Recurse -Filter '*.java' |
        ForEach-Object { $q + ($_.FullName.Replace("$PSScriptRoot\", '') -replace '\\', '/') + $q }
    $srcs += Get-ChildItem "$Work/generated" -Recurse -Filter '*.java' |
        ForEach-Object { $q + ($_.FullName.Replace("$PSScriptRoot\", '') -replace '\\', '/') + $q }
    $bc = ($android -replace '\\', '/') + ';' + ((Join-Path $tool 'core-lambda-stubs.jar') -replace '\\', '/')
    [IO.File]::WriteAllLines("$Work/javac-args.txt",
        @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
          '-bootclasspath', ($q + $bc + $q), '-d', ($q + ($Work -replace '\\', '/') + '/classes' + $q)) + $srcs)
    & "$Java/bin/javac.exe" "@$Work/javac-args.txt"
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
} finally { Pop-Location }

& "$Java/bin/jar.exe" cf "$Work/classes.jar" -C "$Work/classes" .; Check
& "$Java/bin/java.exe" -cp "$tool/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 26 --output "$Work/dex" "$Work/classes.jar"; Check
& "$Java/bin/jar.exe" uf "$Work/unsigned.apk" -C "$Work/dex" classes.dex; Check
& "$tool/zipalign.exe" -f -p 4 "$Work/unsigned.apk" "$Work/aligned.apk"; Check

Write-Host '== 4. sign with the official key ==' -ForegroundColor Cyan
if (Test-Path -LiteralPath $Apk) { Remove-Item -LiteralPath $Apk -Force }
# apksigner prints WARNINGs to stderr on JDK 21+; PowerShell would treat that as
# a failure, so discard stderr and check for the artifact instead.
& "$Java/bin/java.exe" -jar $signer sign --ks $Keystore --ks-pass "pass:$StorePass" `
    --key-pass "pass:$KeyPass" --out $Apk "$Work/aligned.apk" 2>$null
Start-Sleep -Seconds 1
if (!(Test-Path -LiteralPath $Apk)) { throw 'Signing failed; no APK produced.' }

Write-Host '== 5. verify ==' -ForegroundColor Cyan
$verify = & "$Java/bin/java.exe" -jar $signer verify --verbose --print-certs $Apk 2>$null
$verify | Select-String 'Verifies|Verified using v2|Verified using v3|certificate DN|certificate SHA-256'
$vp = [regex]::Match(($verify | Out-String), 'SHA-256 digest:\s*([0-9a-fA-F]{64})')
if (!$vp.Success) { throw 'Cannot read the signed APK fingerprint.' }
$signedPrint = ($vp.Groups[1].Value -replace ':', '').ToUpper()
if ($signedPrint -ne ($Expected -replace ':', '').ToUpper()) {
    throw "Signed APK fingerprint mismatch; not publishable: $signedPrint"
}
& "$tool/aapt2.exe" dump badging $Apk 2>$null | Select-String '^package:'
Get-FileHash -LiteralPath $Apk -Algorithm SHA256

Write-Host ''
Write-Host 'DONE: fingerprint matches the official certificate; upgradeable over the released build.' -ForegroundColor Green

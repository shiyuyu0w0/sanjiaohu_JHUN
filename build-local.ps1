param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Platform = 'android-36.1',
    [string]$BuildTools = '36.1.0',
    [string]$Work = "$PSScriptRoot/../../work/android-build",
    [string]$Apk = "$PSScriptRoot/../Sanjiaohu-1.0.1.apk"
)
$ErrorActionPreference = 'Stop'
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
$manifestText = Get-Content -LiteralPath "$app/AndroidManifest.xml" -Raw
$manifestText.Replace('<manifest ', '<manifest package="cn.jhun.sanjiaohu" ') | Set-Content -LiteralPath "$Work/AndroidManifest.xml" -Encoding utf8
& "$tool/aapt2.exe" link -o "$Work/unsigned.apk" -I $android --manifest "$Work/AndroidManifest.xml" -A "$app/assets" --java "$Work/generated" "$Work/resources.zip"
Check
$sources = @(Get-ChildItem -LiteralPath "$app/java","$Work/generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& "$Java/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$android;$tool/core-lambda-stubs.jar" -d "$Work/classes" @sources
Check
& "$Java/bin/jar.exe" cf "$Work/classes.jar" -C "$Work/classes" .
Check
& "$Java/bin/java.exe" -cp "$tool/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 26 --output "$Work/dex" "$Work/classes.jar"
Check
& "$Java/bin/jar.exe" uf "$Work/unsigned.apk" -C "$Work/dex" classes.dex
Check
& "$tool/zipalign.exe" -f -p 4 "$Work/unsigned.apk" "$Work/aligned.apk"
Check
if (!(Test-Path -LiteralPath "$Work/development.keystore")) {
    & "$Java/bin/keytool.exe" -genkeypair -keystore "$Work/development.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Jiangke Local Build' -keyalg RSA -keysize 2048 -validity 10000
    Check
}
& "$Java/bin/java.exe" -jar "$tool/lib/apksigner.jar" sign --ks "$Work/development.keystore" --ks-pass pass:android --key-pass pass:android --out $Apk "$Work/aligned.apk"
Check
& "$Java/bin/java.exe" -jar "$tool/lib/apksigner.jar" verify --verbose $Apk
Check
Get-FileHash -LiteralPath $Apk -Algorithm SHA256

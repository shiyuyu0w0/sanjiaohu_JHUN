param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$AppBuild = "$PSScriptRoot/../build/ui-review"
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$work = Join-Path $project 'build/ui-instrumentation'
$tool = Join-Path $Sdk 'build-tools/36.1.0'
$android = Join-Path $Sdk 'platforms/android-36.1/android.jar'
New-Item -ItemType Directory -Force -Path "$work/classes","$work/dex" | Out-Null
$manifest = @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.jhun.sanjiaohu.test">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
    <application android:label="Sanjiaohu UI regression" />
    <instrumentation android:name="cn.jhun.sanjiaohu.UiSmokeInstrumentation" android:targetPackage="cn.jhun.sanjiaohu" />
    <instrumentation android:name="cn.jhun.sanjiaohu.GridInstrumentation" android:targetPackage="cn.jhun.sanjiaohu" />
    <instrumentation android:name="cn.jhun.sanjiaohu.UpgradeInstrumentation" android:targetPackage="cn.jhun.sanjiaohu" />
</manifest>
'@
[IO.File]::WriteAllText("$work/AndroidManifest.xml",$manifest,(New-Object Text.UTF8Encoding($false)))
function Check { if ($LASTEXITCODE -ne 0) { throw "UI test build failed: $LASTEXITCODE" } }
& "$tool/aapt2.exe" link -o "$work/unsigned.apk" -I $android --manifest "$work/AndroidManifest.xml"
Check
$sources = @(Get-ChildItem "$project/tests/android" -Filter '*.java' | ForEach-Object FullName)
& "$Java/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$android;$AppBuild/classes" -d "$work/classes" @sources
Check
& "$Java/bin/jar.exe" cf "$work/classes.jar" -C "$work/classes" .
Check
& "$Java/bin/java.exe" -cp "$tool/lib/d8.jar" com.android.tools.r8.D8 --lib $android --classpath "$AppBuild/classes.jar" --min-api 26 --output "$work/dex" "$work/classes.jar"
Check
& "$Java/bin/jar.exe" uf "$work/unsigned.apk" -C "$work/dex" classes.dex
Check
& "$tool/zipalign.exe" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
Check
& "$Java/bin/java.exe" -jar "$tool/lib/apksigner.jar" sign --ks "$project/signing/development.keystore" --ks-pass pass:android --key-pass pass:android --out "$work/ui-tests.apk" "$work/aligned.apk"
Check
Write-Output "Built $work/ui-tests.apk"

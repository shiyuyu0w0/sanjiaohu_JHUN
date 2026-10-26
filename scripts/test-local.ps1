param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Java = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Platform = 'android-36.1',
    [string]$Classes = "$PSScriptRoot/../build/ui-review/classes"
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path $project 'tests'
$output = Join-Path $project 'build/local-tests'
$android = Join-Path $Sdk "platforms/$Platform/android.jar"
if (!(Test-Path -LiteralPath $Classes)) { throw 'Build first using build-local.ps1 -Work build/ui-review.' }
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(Get-ChildItem -LiteralPath $testRoot -File -Filter '*.java' | ForEach-Object FullName)
$stubs = @(Get-ChildItem -LiteralPath "$testRoot/json-stubs","$testRoot/cookie-stubs" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Java/bin/javac.exe" -encoding UTF-8 -cp "$Classes;$android" -d $output @stubs @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
$failures = @()
$passed = 0
foreach ($test in (Get-ChildItem -LiteralPath $testRoot -File -Filter '*.java')) {
    & "$Java/bin/java.exe" -cp "$output;$Classes;$android" "cn.jhun.sanjiaohu.$($test.BaseName)"
    if ($LASTEXITCODE -ne 0) { $failures += $test.Name } else { $passed++ }
}
# Cookie/WebStorage stubs are local-only and never included in the Android build.
& "$Java/bin/java.exe" -cp "$output;$Classes;$android" cn.jhun.sanjiaohu.SessionCookiesTest
if ($LASTEXITCODE -ne 0) { $failures += 'SessionCookiesTest' } else { $passed++ }
foreach ($test in (Get-ChildItem -LiteralPath $testRoot -File -Filter '*.test.cjs')) {
    & node $test.FullName
    if ($LASTEXITCODE -ne 0) { $failures += $test.Name } else { $passed++ }
}
if ($failures.Count -gt 0) { throw "Failed tests: $($failures -join ', ')" }
Write-Output "PASS: $passed local test suites."

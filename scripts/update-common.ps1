$ErrorActionPreference = 'Stop'
$script:UpdateRoot = Split-Path $PSScriptRoot -Parent
$script:UpdateRepo = 'https://github.com/shiyuyu0w0/sanjiaohu_JHUN/'
$script:UpdateCertificate = '2D5C4C7AB3F5E829DFB2273F333A2AF987E512DC270FBE03347638329CDEF22A'

function Invoke-UpdateTool([string]$Executable, [string[]]$Arguments) {
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $Executable @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($code -ne 0) { throw "Update tool failed ($code): $Executable`n$($result | Out-String)" }
    return ($result | Out-String)
}

function Get-UpdateApk([string]$Path, [string]$Java, [string]$Sdk, [string]$BuildTools) {
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt 1 -or $file.Length -gt 268435456) { throw 'APK size is outside supported limits.' }
    $tool = Join-Path $Sdk "build-tools/$BuildTools"
    $signed = Invoke-UpdateTool "$Java/bin/java.exe" @('-jar', "$tool/lib/apksigner.jar", 'verify', '--verbose', '--print-certs', $file.FullName)
    $certs = [regex]::Matches($signed, 'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
    if ($certs.Count -ne 1 -or $certs[0].Groups[1].Value.ToUpperInvariant() -ne $script:UpdateCertificate) { throw 'APK does not use the official signing certificate.' }
    $badging = Invoke-UpdateTool "$tool/aapt2.exe" @('dump', 'badging', $file.FullName)
    $package = [regex]::Match($badging, "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
    $sdkVersion = [regex]::Match($badging, "(?m)^(?:minSdkVersion|sdkVersion):'([0-9]+)'")
    if (!$package.Success -or !$sdkVersion.Success -or $package.Groups[1].Value -ne 'cn.jhun.sanjiaohu') { throw 'Invalid APK package metadata.' }
    if ($package.Groups[3].Value -notmatch '^[0-9]+(\.[0-9]+){1,3}$') { throw 'Invalid version name.' }
    return [pscustomobject]@{ Path=$file.FullName; VersionCode=[long]$package.Groups[2].Value; VersionName=$package.Groups[3].Value; MinSdk=[int]$sdkVersion.Groups[1].Value; Size=[long]$file.Length; Sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
}

function Assert-UpdateManifest($Manifest, $Apk) {
    if ($Manifest.schemaVersion -ne 1 -or $Manifest.channel -cne 'stable' -or $Manifest.packageName -cne 'cn.jhun.sanjiaohu') { throw 'Wrong manifest schema, channel or package.' }
    foreach ($value in @($Manifest.schemaVersion, $Manifest.manifestRevision, $Manifest.versionCode, $Manifest.minSdk, $Manifest.apk.sizeBytes)) {
        if (($value -isnot [int]) -and ($value -isnot [long])) { throw 'Manifest numbers must be integers, not strings.' }
    }
    if ($Manifest.manifestRevision -lt 1 -or $Manifest.manifestRevision -gt 2147483647 -or $Manifest.enabled -isnot [bool]) { throw 'Invalid revision or enabled flag.' }
    if ($Manifest.versionCode -ne $Apk.VersionCode -or $Manifest.versionName -cne $Apk.VersionName -or $Manifest.minSdk -ne $Apk.MinSdk) { throw 'Manifest version does not match APK.' }
    if ($Manifest.apk.sizeBytes -ne $Apk.Size -or $Manifest.apk.sha256 -cne $Apk.Sha256) { throw 'Manifest size/hash does not match final signed APK.' }
    $official = $script:UpdateRepo + 'releases/download/v' + $Apk.VersionName + '/Sanjiaohu-' + $Apk.VersionName + '.apk'
    if ($Manifest.apk.url -cne $official -or $Manifest.releasePage -cne ($script:UpdateRepo + 'releases/tag/v' + $Apk.VersionName)) { throw 'Unexpected release URL.' }
    if (@($Manifest.apk.mirrors).Count -ne 1 -or $Manifest.apk.mirrors[0].id -cne 'ghproxy' -or $Manifest.apk.mirrors[0].url -cne ('https://ghproxy.net/' + $official)) { throw 'Unexpected mirror URL.' }
    if ($Manifest.releaseNotes -isnot [array] -or $Manifest.releaseNotes.Count -gt 30) { throw 'Invalid release notes.' }
    foreach ($line in $Manifest.releaseNotes) { if ($line -isnot [string] -or $line.Length -gt 500) { throw 'Invalid release note.' } }
}

function Test-UpdateLinks($Manifest) {
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    $directory = Join-Path $script:UpdateRoot 'build/update-link-checks'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    try {
        foreach ($initial in @($Manifest.apk.url, $Manifest.apk.mirrors[0].url)) {
            $url = [uri]$initial
            $file = Join-Path $directory ([guid]::NewGuid().ToString() + '.apk')
            $token = New-Object System.Threading.CancellationTokenSource
            $token.CancelAfter(120000)
            try {
                $complete = $false
                for ($redirect = 0; $redirect -lt 6; $redirect++) {
                    $hostName = $url.DnsSafeHost
                    if ($url.Scheme -cne 'https' -or !$url.IsDefaultPort -or $url.UserInfo -or ($hostName -notin @('github.com','ghproxy.net','githubusercontent.com') -and !$hostName.EndsWith('.githubusercontent.com'))) { throw 'Unexpected download redirect host/protocol.' }
                    $response = $client.GetAsync($url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead, $token.Token).GetAwaiter().GetResult()
                    try {
                        $status = [int]$response.StatusCode
                        if ($status -in @(301,302,303,307,308)) { $url = [uri]::new($url, $response.Headers.Location); continue }
                        if ($status -ne 200) { throw "Release download returned HTTP $status. Upload the release asset first." }
                        $inputStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                        $outputStream = [IO.File]::Create($file)
                        try {
                            $buffer = New-Object byte[] 65536
                            $total = 0L
                            while (($read = $inputStream.ReadAsync($buffer, 0, $buffer.Length, $token.Token).GetAwaiter().GetResult()) -gt 0) {
                                $total += $read
                                if ($total -gt $Manifest.apk.sizeBytes) { throw 'Remote APK exceeds expected size.' }
                                $outputStream.Write($buffer, 0, $read)
                            }
                        } finally { $outputStream.Dispose(); $inputStream.Dispose() }
                        if ($total -ne $Manifest.apk.sizeBytes -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Manifest.apk.sha256) { throw 'Remote APK is different from the signed release artifact.' }
                        Write-Output "Verified download: $initial"
                        $complete = $true
                        break
                    } finally { $response.Dispose() }
                }
                if (!$complete) { throw 'Too many download redirects.' }
            } finally { $token.Dispose(); if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file -Force } }
        }
    } finally { $client.Dispose(); $handler.Dispose() }
}

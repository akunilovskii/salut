$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$WrapperDirectory = Join-Path $ProjectRoot 'build\gradle-wrapper'
$WrapperJar = Join-Path $WrapperDirectory 'gradle-wrapper.jar'
$ExpectedHash = 'e2b82129ab64751fd40437007bd2f7f2afb3c6e41a9198e628650b22d5824a14'
$SourceUrl = 'https://raw.githubusercontent.com/aleixrodriala/newtube/v1.17.1/gradle/wrapper/gradle-wrapper.jar'

function Get-FileSha256([string]$Path) {
    $Sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($Sha256.ComputeHash([IO.File]::ReadAllBytes($Path))).Replace('-', '').ToLowerInvariant()
    } finally {
        $Sha256.Dispose()
    }
}

New-Item -ItemType Directory -Path $WrapperDirectory -Force | Out-Null

if (-not (Test-Path -LiteralPath $WrapperJar) -or
    (Get-FileSha256 $WrapperJar) -ne $ExpectedHash) {
    $DownloadPath = Join-Path $WrapperDirectory 'gradle-wrapper.download'
    try {
        $Downloader = [Net.WebClient]::new()
        try { $Downloader.DownloadFile($SourceUrl, $DownloadPath) } finally { $Downloader.Dispose() }
        if ((Get-FileSha256 $DownloadPath) -ne $ExpectedHash) {
            throw 'Downloaded Gradle Wrapper JAR does not match the pinned SHA-256.'
        }
        Move-Item -LiteralPath $DownloadPath -Destination $WrapperJar -Force
    } finally {
        Remove-Item -LiteralPath $DownloadPath -Force -ErrorAction SilentlyContinue
    }
}

Copy-Item -LiteralPath (Join-Path $ProjectRoot 'gradle\wrapper\gradle-wrapper.properties') -Destination (Join-Path $WrapperDirectory 'gradle-wrapper.properties') -Force

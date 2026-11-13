param(
    [string[]]$Tasks = @(':smarttubetv:assembleStmobileDebug'),
    [string[]]$GradleArguments = @(),
    [string]$ToolchainRoot
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$Manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal\manifest.json') -Raw | ConvertFrom-Json
$KeyPath = if ($env:SALUT_SIGNING_KEY) { $env:SALUT_SIGNING_KEY } else { Join-Path $ProjectRoot $Manifest.application.key }
if ((Test-Path -LiteralPath $KeyPath) -and
    (Get-FileHash -LiteralPath $KeyPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Manifest.application.keyFileSha256) {
    throw 'The retained signing key changed. Restore its exact bytes before building.'
}
if (($GradleArguments -contains '-PsalutRelease') -and -not (Test-Path -LiteralPath $KeyPath)) {
    throw 'Set SALUT_SIGNING_KEY to the retained release key before publishing.'
}

if (-not $ToolchainRoot) {
    foreach ($Candidate in @((Join-Path $ProjectRoot 'build\toolchain'))) {
        if (Test-Path -LiteralPath (Join-Path $Candidate 'java\bin\java.exe')) {
            $ToolchainRoot = [IO.Path]::GetFullPath($Candidate)
            break
        }
    }
}
if ($ToolchainRoot) {
    $env:JAVA_HOME = Join-Path $ToolchainRoot 'java'
    $env:ANDROID_HOME = Join-Path $ToolchainRoot 'android-sdk'
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:ANDROID_USER_HOME = Join-Path $ToolchainRoot 'android-user'
    $env:GRADLE_USER_HOME = Join-Path $ToolchainRoot 'gradle-user-home'
} else {
    if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) {
        throw 'Set JAVA_HOME and ANDROID_HOME, or supply -ToolchainRoot. See PERSONAL.md.'
    }
    $env:GRADLE_USER_HOME = Join-Path $ProjectRoot 'build\gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $ProjectRoot 'build\android-user'
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
foreach ($Required in @("$env:JAVA_HOME\bin\java.exe", "$env:ANDROID_HOME\platforms\android-37.0\android.jar")) {
    if (-not (Test-Path -LiteralPath $Required)) { throw "Missing build toolchain input: $Required" }
}
$ProjectCache = Join-Path $ProjectRoot 'build\gradle-project-cache'
& (Join-Path $PSScriptRoot 'Ensure-TestFixture.ps1')
& (Join-Path $ProjectRoot 'gradlew.bat') '-p' $ProjectRoot '--project-cache-dir' $ProjectCache '--console=plain' '--max-workers=4' @Tasks @GradleArguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }

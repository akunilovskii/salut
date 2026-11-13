param(
    [string]$Target = 'upstream/main',
    [string]$Branch,
    [string]$ToolchainRoot
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$Manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal\manifest.json') -Raw | ConvertFrom-Json
$Baseline = $Manifest.upstream.baselineCommit

$Changes = & git -C $ProjectRoot status --porcelain --untracked-files=all
if ($LASTEXITCODE -ne 0 -or $Changes) { throw 'Commit or preserve working changes before preparing an upstream update.' }
if ((& git -C $ProjectRoot branch --show-current) -ne 'main') { throw 'Start from the personal main branch.' }

& git -C $ProjectRoot fetch --no-recurse-submodules upstream
if ($LASTEXITCODE -ne 0) { throw 'Upstream fetch failed.' }
$TargetRevision = & git -C $ProjectRoot rev-parse --verify ($Target + '^{commit}')
if ($LASTEXITCODE -ne 0) { throw "Could not resolve the upstream target: $Target" }
& git -C $ProjectRoot merge-base --is-ancestor $Baseline $TargetRevision
if ($LASTEXITCODE -ne 0) { throw "The pinned upstream baseline is not an ancestor of $Target." }

$SourcePaths = @(
    '.gitignore', '.gitmodules', 'LICENSE', 'PRIVACY.md', 'THIRD_PARTY_NOTICES.md',
    'build.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'settings.gradle',
    'MediaServiceCore', 'SharedModules', 'common', 'doubletapplayerview-media3',
    'filepicker-lib', 'gradle', 'macrobenchmark', 'sabr-media3', 'slidableactivity',
    'smarttubetv', 'tools',
    ':(exclude)gradle/wrapper/gradle-wrapper.jar',
    ':(exclude)smarttubetv/src/debug/assets/ttff-fixture.mp4',
    ':(exclude)smarttubetv/src/debug/assets/sabr'
)
& git -C $ProjectRoot diff --quiet $Baseline $TargetRevision -- @SourcePaths
$DiffExitCode = $LASTEXITCODE
if ($DiffExitCode -gt 1) { throw 'Could not compare upstream source revisions.' }
if ($DiffExitCode -eq 0) {
    Write-Output "No source changes between $Baseline and $TargetRevision."
    return
}

$PatchDirectory = Join-Path $ProjectRoot 'build\maintenance'
New-Item -ItemType Directory -Path $PatchDirectory -Force | Out-Null
$PatchPath = Join-Path $PatchDirectory 'upstream-source.patch'
# Let Git write the patch bytes directly, preserving binary payloads and line endings.
& git -C $ProjectRoot diff --binary --full-index "--output=$PatchPath" $Baseline $TargetRevision -- @SourcePaths
if ($LASTEXITCODE -ne 0) { throw 'Could not write the upstream source patch.' }

if (-not $Branch) { $Branch = 'update/upstream-' + (Get-Date -Format 'yyyyMMdd-HHmmss') }
& git -C $ProjectRoot switch -c $Branch
if ($LASTEXITCODE -ne 0) { throw 'Could not create the update branch.' }
& git -C $ProjectRoot apply --3way --index $PatchPath
if ($LASTEXITCODE -ne 0) {
    & git -C $ProjectRoot diff --name-only --diff-filter=U
    throw "The source patch needs conflict resolution on $Branch. Keep behavior from personal/features.json."
}

& git -C $ProjectRoot submodule update --init --recursive
if ($LASTEXITCODE -ne 0) { throw 'Pinned submodule update failed.' }
& (Join-Path $PSScriptRoot 'Test.ps1') -ToolchainRoot $ToolchainRoot
& (Join-Path $PSScriptRoot 'Build.ps1') -ToolchainRoot $ToolchainRoot
Write-Output "Source patch applied and validated on $Branch. Review it, set the baseline in personal/manifest.json to $TargetRevision and commit."

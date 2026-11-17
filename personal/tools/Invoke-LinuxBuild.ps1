param(
    [ValidateSet('Test', 'Build', 'Prepare', 'Run')][string]$Action = 'Test',
    [hashtable]$TestArguments = @{},
    [string[]]$Tasks = @(':smarttubetv:assembleStmobileDebug'),
    [string[]]$GradleArguments = @(),
    [string]$ArgumentsPath
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))

if ($Action -in @('Prepare', 'Run')) {
    if ($env:SALUT_BUILD_CONTAINER -ne '1') { throw 'Container-only operation.' }
    $Configuration = Get-Content -LiteralPath $ArgumentsPath -Raw | ConvertFrom-Json -AsHashtable
    if ($Action -eq 'Prepare') {
        foreach ($Module in $Configuration.Modules) {
            $Directory = [IO.Path]::GetFullPath((Join-Path '/cache' $Module.Subpath))
            if (-not $Directory.StartsWith('/cache/', [StringComparison]::Ordinal)) { throw 'Invalid build directory.' }
            New-Item -ItemType Directory -Force -Path $Directory | Out-Null
        }
        return
    }

    $Started = [DateTime]::UtcNow.AddSeconds(-1)
    $Succeeded = $false
    try {
        if ($Configuration.Action -eq 'Test') {
            $Arguments = $Configuration.TestArguments
            & (Join-Path $PSScriptRoot 'Test.ps1') -Native @Arguments
        } else {
            & (Join-Path $PSScriptRoot 'Build.ps1') -Native -Tasks $Configuration.Tasks -GradleArguments $Configuration.GradleArguments
            if ($Configuration.GradleArguments -contains '-PsalutRelease' -and
                $Configuration.Tasks -contains ':smarttubetv:assembleStmobileRelease') {
                $Release = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal/release.json') -Raw | ConvertFrom-Json
                & python -B -X utf8 (Join-Path $ProjectRoot 'tools/verify_release.py') --tag "v$($Release.versionName)" --dist (Join-Path $ProjectRoot 'smarttubetv/build/outputs/renamed_apks/stmobileRelease')
            }
        }
        $Succeeded = $true
    } finally {
        $LogDirectory = Join-Path $ProjectRoot 'build/maintenance'
        if (Test-Path -LiteralPath $LogDirectory) {
            Get-ChildItem -LiteralPath $LogDirectory -File | Where-Object {
                $_.LastWriteTimeUtc -ge $Started -and ($_.Name -eq 'test-metrics.json' -or $_.Name -like 'tests-*.log')
            } | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination '/host-results' }
        }
        if ($Succeeded -and $Configuration.Action -eq 'Build') {
            foreach ($Variant in @('Debug', 'Release')) {
                if ($Configuration.Tasks -notcontains ":smarttubetv:assembleStmobile$Variant") { continue }
                $Source = Join-Path $ProjectRoot "smarttubetv/build/outputs/renamed_apks/stmobile$Variant"
                if (-not (Test-Path -LiteralPath $Source)) { continue }
                $Destination = "/host-apks/stmobile$Variant"
                New-Item -ItemType Directory -Force -Path $Destination | Out-Null
                Get-ChildItem -LiteralPath $Source -File | ForEach-Object {
                    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $Destination $_.Name) -Force
                }
            }
        }
    }
    return
}

$Lock = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal/container/image.json') -Raw | ConvertFrom-Json
if ($Lock.image -notmatch '^ghcr\.io/akunilovskii/salut-build@sha256:[a-f0-9]{64}$') { throw 'An immutable Salut build image is required.' }
$RecipeHash = (Get-FileHash -LiteralPath (Join-Path $ProjectRoot 'personal/container/Dockerfile') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($RecipeHash -ne $Lock.recipeSha256) { throw 'Build image recipe changed. Publish and qualify its new image before using it.' }
if ($ProjectRoot.Contains(',')) { throw 'Docker bind mounts require a checkout path without commas.' }
$Server = & docker info --format '{{.OSType}}/{{.Architecture}}'
if ($Server -notmatch '^linux/(x86_64|amd64)$') { throw 'Select a Linux AMD64 Docker engine.' }

$Paths = @(& git -C $ProjectRoot ls-files --cached --others --exclude-standard -- '*build.gradle')
$Modules = @()
foreach ($Path in $Paths | Sort-Object -Unique) {
    $Relative = $Path.Replace('\', '/') -replace '(^|/)build\.gradle$', ''
    if (-not $Relative -and $Path -ne 'build.gradle') { continue }
    $BuildPath = if ($Relative) { "$Relative/build" } else { 'build' }
    $HostPath = [IO.Path]::GetFullPath((Join-Path $ProjectRoot $BuildPath))
    if (-not $HostPath.StartsWith($ProjectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build path escaped checkout.' }
    New-Item -ItemType Directory -Force -Path $HostPath | Out-Null
    $Modules += @{ BuildPath = $BuildPath; Subpath = $(if ($Relative) { "modules/$Relative" } else { 'root' }) }
}
if (-not ($Modules | Where-Object BuildPath -eq 'build')) { throw 'Root Gradle project not found.' }
$CheckoutHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($ProjectRoot))).Substring(0, 16).ToLowerInvariant()
$Volume = "salut-linux-$CheckoutHash"
$RunDirectory = Join-Path $ProjectRoot ('build/maintenance/container-runs/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null
$ConfigurationPath = Join-Path $RunDirectory 'arguments.json'
@{ Action = $Action; TestArguments = $TestArguments; Tasks = $Tasks; GradleArguments = $GradleArguments; Modules = $Modules } |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ConfigurationPath -Encoding utf8NoBOM
$Common = @('run', '--rm', '--platform', 'linux/amd64', '--workdir', '/workspace',
    '--label', "io.github.salut.checkout=$CheckoutHash",
    '--mount', "type=bind,src=$ProjectRoot,dst=/workspace",
    '--mount', "type=bind,src=$ConfigurationPath,dst=/run/salut-arguments.json,readonly")
& docker @Common --mount "type=volume,src=$Volume,dst=/cache" $Lock.image pwsh -NoProfile -File /workspace/personal/tools/Invoke-LinuxBuild.ps1 -Action Prepare -ArgumentsPath /run/salut-arguments.json
$RunArguments = $Common + @('--cpus', '4', '--memory', '8g', '--mount', "type=bind,src=$RunDirectory,dst=/host-results")
foreach ($Module in $Modules) {
    $RunArguments += @('--mount', "type=volume,src=$Volume,dst=/workspace/$($Module.BuildPath),volume-subpath=$($Module.Subpath)")
}
if ($Action -eq 'Build') {
    $ApkDirectory = Join-Path $ProjectRoot 'smarttubetv/build/outputs/renamed_apks'
    New-Item -ItemType Directory -Force -Path $ApkDirectory | Out-Null
    $RunArguments += @('--mount', "type=bind,src=$ApkDirectory,dst=/host-apks")
    if ($GradleArguments -contains '-PsalutRelease') {
        $Manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal/manifest.json') -Raw | ConvertFrom-Json
        $Key = if ($env:SALUT_SIGNING_KEY) { $env:SALUT_SIGNING_KEY } else { Join-Path $ProjectRoot $Manifest.application.key }
        if (-not (Test-Path -LiteralPath $Key) -or
            (Get-FileHash -LiteralPath $Key -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Manifest.application.keyFileSha256) {
            throw 'Set SALUT_SIGNING_KEY to the retained release key.'
        }
        $RunArguments += @('--mount', "type=bind,src=$([IO.Path]::GetFullPath($Key)),dst=/run/salut-signing.keystore,readonly", '--env', 'SALUT_SIGNING_KEY=/run/salut-signing.keystore')
    }
}
$Revision = & git -C $ProjectRoot rev-parse HEAD
$Dirty = @(& git -C $ProjectRoot status --porcelain --untracked-files=normal).Count -ne 0
$RunArguments += @('--env', "GITHUB_SHA=$Revision", '--env', "SALUT_SOURCE_DIRTY=$($Dirty.ToString().ToLowerInvariant())")
Write-Output "Linux image: $($Lock.image)"
Write-Output "Build volume: $Volume. Run evidence: $RunDirectory"
& docker @RunArguments $Lock.image pwsh -NoProfile -File /workspace/personal/tools/Invoke-LinuxBuild.ps1 -Action Run -ArgumentsPath /run/salut-arguments.json

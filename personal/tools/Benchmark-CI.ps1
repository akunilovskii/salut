param([string]$ToolchainRoot)

$ErrorActionPreference = 'Stop'
if (-not $IsLinux) { throw 'Worker reuse qualification must run on Linux.' }
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$Output = Join-Path $ProjectRoot 'build/maintenance/ci-benchmark'
New-Item -ItemType Directory -Path $Output -Force | Out-Null
$Arguments = @{ FullSuite = $true; OfflineOnly = $true; ForceExecution = $true }
if ($ToolchainRoot) { $Arguments.ToolchainRoot = $ToolchainRoot }
# Exclude dependency downloads and initial compilation from the paired timings.
# Run every offline test. Live service/account checks remain a separate explicit mode.
# Any offline failure stops qualification; no per-trial selection is allowed.
$Trials = @('warmup', 'isolated', 'reuse', 'reuse', 'isolated')
$Measured = @()
for ($Index = 0; $Index -lt $Trials.Count; $Index++) {
    $Mode = $Trials[$Index]
    $Arguments.ReusePhoneWorkers = $Mode -eq 'reuse'
    $Arguments.IsolatePhoneWorkers = $Mode -ne 'reuse'
    $Trial = Join-Path $Output ("$Index-$Mode")
    New-Item -ItemType Directory -Path $Trial -Force | Out-Null
    try {
        & (Join-Path $PSScriptRoot 'Test.ps1') @Arguments
    } finally {
        foreach ($Name in @('test-metrics.json', 'tests-common.log', 'tests-notification-provider.log', 'tests-phone.log')) {
            $Source = Join-Path $ProjectRoot ('build/maintenance/' + $Name)
            if (Test-Path -LiteralPath $Source) { Copy-Item -LiteralPath $Source -Destination (Join-Path $Trial $Name) }
        }
    }
    if ($Mode -ne 'warmup') { $Measured += Join-Path $Trial 'test-metrics.json' }
}
& python -X utf8 (Join-Path $ProjectRoot 'tools/ci_metrics.py') compare @Measured --out (Join-Path $Output 'comparison.json')
if ($LASTEXITCODE) { throw 'Execution inventories do not qualify a performance comparison.' }

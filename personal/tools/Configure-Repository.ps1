$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$Manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'personal\manifest.json') -Raw | ConvertFrom-Json
$Remotes = @(& git -C $ProjectRoot remote)
if ($Remotes -notcontains 'upstream') {
    & git -C $ProjectRoot remote add upstream $Manifest.upstream.url
    if ($LASTEXITCODE -ne 0) { throw 'Could not register upstream.' }
}
& git -C $ProjectRoot remote set-url upstream $Manifest.upstream.url
& git -C $ProjectRoot config remote.upstream.fetch '+refs/heads/*:refs/remotes/upstream/*'
& git -C $ProjectRoot config remote.upstream.pushurl 'no-push://upstream'
& git -C $ProjectRoot config fetch.recurseSubmodules false
& git -C $ProjectRoot config core.longpaths true
& git -C $ProjectRoot config merge.conflictStyle zdiff3
& git -C $ProjectRoot config rerere.enabled true
& git -C $ProjectRoot config rerere.autoUpdate false
if ($LASTEXITCODE -ne 0) { throw 'Could not configure local repository settings.' }
foreach ($Module in @('MediaServiceCore', 'SharedModules', 'MediaServiceCore\SharedModules')) {
    $ModulePath = Join-Path $ProjectRoot $Module
    if (Test-Path -LiteralPath (Join-Path $ModulePath '.git')) {
        & git -C $ModulePath config remote.origin.pushurl 'no-push://upstream'
        if ($LASTEXITCODE -ne 0) { throw "Could not protect the upstream submodule remote: $Module" }
    }
}
Write-Output 'Configured upstream, local conflict history and Windows path support.'

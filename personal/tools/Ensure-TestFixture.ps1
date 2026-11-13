$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$AssetDirectory = Join-Path $ProjectRoot 'smarttubetv\build\generated\ttffFixtureAssets'
$SourceBase = 'https://raw.githubusercontent.com/aleixrodriala/newtube/v1.17.1/smarttubetv/src/debug/assets/'
$Fixtures = @(
    @{ Path = 'ttff-fixture.mp4'; Hash = '0118627927aa8f92c47d4d7378bc8ab4c9d0dd0f0c8cd0469518d8e32f4a6e56' },
    @{ Path = 'sabr/aac.mp4'; Hash = '5055eecba3a5e449b1ffff2cd2cc459b39951fad746e254d11fe5b87f2b2d985' },
    @{ Path = 'sabr/avc-low.mp4'; Hash = '7013b02c9818c97177156be0bbb65fa1b8f3bed9807acfd9fee2551024e726d1' },
    @{ Path = 'sabr/avc.mp4'; Hash = '58867f03243bcf13d88a8ac020b2130accde92aba558219a00703fa38c826d56' },
    @{ Path = 'sabr/opus.webm'; Hash = '43c8ae104208075d9f19793d53ada616796dfec04285439969f9974d42eb4f31' },
    @{ Path = 'sabr/vp9.webm'; Hash = '8d033c55bd94bc0ca4763e639b759875752291f735ce618e01f9a308e99dab5d' }
)

foreach ($Fixture in $Fixtures) {
    $FixturePath = Join-Path $AssetDirectory $Fixture.Path
    New-Item -ItemType Directory -Path (Split-Path -Parent $FixturePath) -Force | Out-Null
    if ((Test-Path -LiteralPath $FixturePath) -and
        (Get-FileHash -LiteralPath $FixturePath -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Fixture.Hash) {
        continue
    }

    $DownloadPath = "$FixturePath.download"
    try {
        Invoke-WebRequest -Uri ($SourceBase + $Fixture.Path) -OutFile $DownloadPath
        if ((Get-FileHash -LiteralPath $DownloadPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Fixture.Hash) {
            throw "Downloaded test fixture $($Fixture.Path) does not match the pinned SHA-256."
        }
        Move-Item -LiteralPath $DownloadPath -Destination $FixturePath -Force
    } finally {
        if (Test-Path -LiteralPath $DownloadPath) {
            [IO.File]::Delete($DownloadPath)
        }
    }
}

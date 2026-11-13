$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$sourcePath = Join-Path $repositoryRoot 'personal\assets\salut-wordmark-source.svg'
$outlinePath = Join-Path $repositoryRoot 'personal\assets\salut-wordmark.svg'
$vectorPath = Join-Path $repositoryRoot 'smarttubetv\src\stmobile\res\drawable\ic_salut_toolbar_logo.xml'
$inkscapePath = 'C:\Program Files\Inkscape\bin\inkscape.com'

if (-not (Test-Path -LiteralPath $inkscapePath)) {
    throw 'Inkscape is required to export the wordmark outlines.'
}
& $inkscapePath $sourcePath --export-text-to-path --export-plain-svg "--export-filename=$outlinePath"
if ($LASTEXITCODE -ne 0) {
    throw "Inkscape export failed with exit code $LASTEXITCODE."
}

[xml]$outline = Get-Content -LiteralPath $outlinePath -Raw
$width = $outline.DocumentElement.GetAttribute('width')
$height = $outline.DocumentElement.GetAttribute('height')
$viewBox = $outline.DocumentElement.GetAttribute('viewBox') -split '\s+'
if ($viewBox.Count -ne 4 -or $viewBox[0] -ne '0' -or $viewBox[1] -ne '0') {
    throw 'The wordmark SVG must have a four-number viewBox starting at zero.'
}
function Get-PathData([string]$id) {
    $node = $outline.SelectSingleNode("//*[@id='$id']")
    if ($null -eq $node -or $node.LocalName -ne 'path') {
        throw "Missing outlined path: $id"
    }
    return [System.Security.SecurityElement]::Escape($node.GetAttribute('d'))
}

function Get-PathTranslateX([string]$id) {
    $node = $outline.SelectSingleNode("//*[@id='$id']")
    $transform = $node.GetAttribute('transform')
    if ([string]::IsNullOrEmpty($transform)) {
        return '0'
    }
    if ($transform -notmatch '^translate\((-?[\d.]+) 0\)$') {
        throw "Unexpected transform for $id`: $transform"
    }
    return $Matches[1]
}

$iconBackground = Get-PathData 'play-background'
$iconMark = Get-PathData 'play-mark'
$letters = @('letter-s0', 'letter-a1', 'letter-l2', 'letter-u3', 'letter-t4')
$letterPaths = foreach ($id in $letters) {
    '    <group android:translateX="{0}"><path android:fillColor="@android:color/white" android:pathData="{1}" /></group>' -f (Get-PathTranslateX $id), (Get-PathData $id)
}
$letterMarkup = $letterPaths -join [Environment]::NewLine
$vector = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- Inkscape outlines from personal/assets/salut-wordmark-source.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${width}dp"
    android:height="${height}dp"
    android:viewportWidth="$($viewBox[2])"
    android:viewportHeight="$($viewBox[3])">
    <path android:fillColor="#76243D" android:pathData="$iconBackground" />
    <path android:fillColor="@android:color/white" android:pathData="$iconMark" />
$letterMarkup
</vector>
"@
[System.IO.File]::WriteAllText($vectorPath, $vector + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false))

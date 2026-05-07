[CmdletBinding()]
param(
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'manifest.json'),
    [string]$OutputRoot = '',
    [string[]]$SourceId,
    [string[]]$SampleId,
    [switch]$ListOnly,
    [switch]$IncludeManual
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-LibraryDirectory {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-NormalizedArray {
    param($Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return @($Value)
    }
    return @($Value)
}

function Get-NormalizedStringArray {
    param($Value)

    return @(Get-NormalizedArray -Value $Value | Where-Object {
        $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_)
    } | ForEach-Object { [string]$_ })
}

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Manifest not found: $ManifestPath"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -Depth 12
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $PSScriptRoot 'downloads'
}

$sourceFilter = @(Get-NormalizedStringArray -Value $SourceId)
$sampleFilter = @(Get-NormalizedStringArray -Value $SampleId)

New-LibraryDirectory -Path $OutputRoot
New-LibraryDirectory -Path (Join-Path $PSScriptRoot 'templates')
New-LibraryDirectory -Path (Join-Path $PSScriptRoot 'templates\intake')
New-LibraryDirectory -Path (Join-Path $PSScriptRoot 'templates\curated')
New-LibraryDirectory -Path (Join-Path $PSScriptRoot 'templates\generated')

$selectedSources = @($manifest.sources)
if ($sourceFilter.Count -gt 0) {
    $selectedSources = @($selectedSources | Where-Object { $sourceFilter -contains $_.id })
}

$downloadPlan = New-Object System.Collections.Generic.List[object]
foreach ($source in $selectedSources) {
    foreach ($sample in (Get-NormalizedArray -Value $source.samples)) {
        if ($sampleFilter.Count -gt 0 -and $sampleFilter -notcontains $sample.id) {
            continue
        }

        $downloadPlan.Add([PSCustomObject]@{
            SourceId        = $source.id
            SourceName      = $source.name
            SampleId        = $sample.id
            FileName        = $sample.fileName
            Modality        = $sample.modality
            ObjectType      = $sample.objectType
            AutomationTier  = $source.automationTier
            AcquisitionMode = $sample.acquisition.mode
            Url             = $sample.acquisition.url
        })
    }
}

if ($downloadPlan.Count -eq 0) {
    Write-Warning 'No samples matched the current filters.'
    return
}

if ($ListOnly) {
    $downloadPlan |
        Sort-Object SourceId, SampleId |
        Format-Table SourceId, SampleId, Modality, ObjectType, AcquisitionMode, Url -AutoSize
    return
}

$downloaded = 0
$skipped = 0
$manual = 0

foreach ($item in $downloadPlan | Sort-Object SourceId, SampleId) {
    $targetDir = Join-Path $OutputRoot $item.SourceId
    New-LibraryDirectory -Path $targetDir

    switch ($item.AcquisitionMode) {
        'raw-file' {
            $targetFile = Join-Path $targetDir $item.FileName
            if (Test-Path -LiteralPath $targetFile) {
                Write-Host "[skip]   $($item.SampleId) -> $targetFile"
                $skipped++
                continue
            }

            Write-Host "[fetch]  $($item.SampleId) -> $targetFile"
            Invoke-WebRequest -Uri $item.Url -OutFile $targetFile
            $downloaded++
        }
        default {
            if ($IncludeManual) {
                Write-Host "[manual] $($item.SampleId) ($($item.AcquisitionMode)) -> $($item.Url)"
            }
            $manual++
        }
    }
}

Write-Host ''
Write-Host "Downloaded: $downloaded"
Write-Host "Skipped:    $skipped"
Write-Host "Manual:     $manual"

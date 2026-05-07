[CmdletBinding()]
param(
    [string]$SourceId = 'pydicom-test-files',
    [string]$SampleId = 'pydicom-ct-small'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
    & .\sample-library\download-public-samples.ps1 -SourceId $SourceId -SampleId $SampleId
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    & mvn -q -pl pacs-test -am -Dtest=PublicSampleLibraryIntegrationTest '-Dsurefire.failIfNoSpecifiedTests=false' test
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

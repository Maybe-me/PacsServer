[CmdletBinding()]
param(
    [string]$SourceId = 'pydicom-test-files',
    [string]$SampleId = 'pydicom-ct-small'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$generatorRoot = Join-Path $repoRoot 'dicom-generator'
$pythonExecutable = (& python -c "import sys; print(sys.executable)" | Select-Object -Last 1).Trim()
if (-not $pythonExecutable) {
    throw 'Failed to resolve python executable path.'
}

Push-Location $repoRoot
try {
    & .\sample-library\download-public-samples.ps1 -SourceId $SourceId -SampleId $SampleId
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Push-Location $generatorRoot
    try {
        python -m pip install --quiet -r requirements.txt
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    finally {
        Pop-Location
    }

    & mvn -q -pl pacs-test -am -Dtest=PythonGeneratorScheduledPullIntegrationTest '-Dsurefire.failIfNoSpecifiedTests=false' "-Dpython.executable=$pythonExecutable" test
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

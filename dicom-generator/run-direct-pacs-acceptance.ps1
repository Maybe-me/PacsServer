[CmdletBinding()]
param(
    [string]$SourceId = 'pydicom-test-files',
    [string]$SampleId = 'pydicom-ct-small'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$generatorRoot = Join-Path $repoRoot 'dicom-generator'
$outputDirectory = Join-Path $generatorRoot 'output\acceptance\direct'

Push-Location $repoRoot
try {
    & .\sample-library\download-public-samples.ps1 -SourceId $SourceId -SampleId $SampleId
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    if ($SourceId -ne 'pydicom-test-files' -or $SampleId -ne 'pydicom-ct-small') {
        throw 'Current direct PACS acceptance runner expects SourceId=pydicom-test-files and SampleId=pydicom-ct-small.'
    }

    $templatePath = Join-Path $repoRoot 'sample-library\downloads\pydicom-test-files\pydicom-CT_small.dcm'
    $patientId = 'PYGEN-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $accessionNumber = 'ACC' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

    Push-Location $generatorRoot
    try {
        $generatedPath = @"
from app.models import DicomIdentityOverrides, GenerateJobRequest
from app.service import GeneratorService

request = GenerateJobRequest(
    template_path=r"$templatePath",
    output_directory=r"$outputDirectory",
    overwrite=True,
    identity=DicomIdentityOverrides(
        patient_id="$patientId",
        patient_name="Pygen^Acceptance",
        accession_number="$accessionNumber",
        study_date="20260504",
        study_time="120000",
    ),
    pixel_transform={"flip_horizontal": True},
    tag_overrides={"StudyDescription": "Python Generator Acceptance"},
)
result = GeneratorService().export(request, job_id="direct-acceptance")
print(result.instances[0].output_path)
"@ | python -
    }
    finally {
        Pop-Location
    }

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $generatedFile = ($generatedPath | Select-Object -Last 1).Trim()
    if (-not $generatedFile) {
        throw 'Failed to determine generated acceptance file path.'
    }

    & mvn -q -pl pacs-test -am -Dtest=PythonGeneratorDirectPacsIntegrationTest '-Dsurefire.failIfNoSpecifiedTests=false' "-Dpygen.acceptance.file=$generatedFile" test
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

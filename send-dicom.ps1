param(
    [string]$InputPath,
    [string]$TargetHost = "127.0.0.1",
    [int]$Port = 11112,
    [string]$CalledAet = "MY_PACS",
    [string]$CallingAet = "LOCAL_SEND",
    [switch]$Rebuild,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jarPath = Join-Path $repoRoot "pacs-boot\target\pacs-boot-0.1.0-SNAPSHOT-exec.jar"

if ($Rebuild -or -not (Test-Path $jarPath)) {
    Push-Location $repoRoot
    try {
        mvn -q -pl pacs-boot -am -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    finally {
        Pop-Location
    }
}

$javaArgs = @(
    "-Dloader.main=com.mylife.pacs.boot.tool.LocalDicomSender"
    "-cp"
    $jarPath
    "org.springframework.boot.loader.launch.PropertiesLauncher"
)

if ($Help) {
    $javaArgs += "--help"
}
else {
    if ([string]::IsNullOrWhiteSpace($InputPath)) {
        throw "InputPath is required unless -Help is used."
    }
    $javaArgs += @(
        "--path"
        $InputPath
        "--host"
        $TargetHost
        "--port"
        $Port
        "--called-aet"
        $CalledAet
        "--calling-aet"
        $CallingAet
    )
}

& java @javaArgs
exit $LASTEXITCODE

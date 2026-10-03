# Downloads the Polish Vosk model (about 50 MB, Apache 2.0) into backend\models\.
# Idempotent: does nothing if the model directory already exists.
$ErrorActionPreference = 'Stop'

$ModelName = 'vosk-model-small-pl-0.22'
$ModelUrl = "https://alphacephei.com/vosk/models/$ModelName.zip"
$Root = Split-Path -Parent $PSScriptRoot
$Target = Join-Path $Root 'backend\models'
$ModelDir = Join-Path $Target $ModelName

if (Test-Path $ModelDir) {
    Write-Host "Model already present: $ModelDir"
    exit 0
}

New-Item -ItemType Directory -Force -Path $Target | Out-Null
$Zip = Join-Path ([System.IO.Path]::GetTempPath()) "$ModelName.zip"

try {
    Write-Host "Downloading $ModelUrl"
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $ModelUrl -OutFile $Zip -UseBasicParsing
    } catch {
        Write-Error "Could not download the Vosk model from $ModelUrl. Check your connection or the address at https://alphacephei.com/vosk/models"
        exit 1
    }
    try {
        Expand-Archive -Path $Zip -DestinationPath $Target -Force
    } catch {
        if (Test-Path $ModelDir) { Remove-Item -Recurse -Force $ModelDir }
        Write-Error 'The downloaded file is not a valid zip archive.'
        exit 1
    }
    if (-not (Test-Path $ModelDir)) {
        Write-Error "Archive did not contain $ModelName."
        exit 1
    }
    Write-Host "Model ready: $ModelDir"
} finally {
    if (Test-Path $Zip) { Remove-Item -Force $Zip }
}

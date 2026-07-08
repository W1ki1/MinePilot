param(
    [string]$ProjectRoot = "G:\MinePilot\MinePilot\mc_ai_recorder"
)

$ErrorActionPreference = "Stop"

$BundleRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PayloadRoot = Join-Path $BundleRoot "payload"

if (-not (Test-Path $ProjectRoot -PathType Container)) {
    throw "Project directory does not exist: $ProjectRoot"
}

if (-not (Test-Path $PayloadRoot -PathType Container)) {
    throw "Bundle payload is missing: $PayloadRoot"
}

$SchemaFile = Join-Path $ProjectRoot "src\client\java\pl\pixeloza\mc_ai_recorder\client\recording\RecordingSchema.java"
if (-not (Test-Path $SchemaFile -PathType Leaf)) {
    throw "RecordingSchema.java is missing. Apply recorder stage 1 first."
}

$CurrentSchema = Get-Content -LiteralPath $SchemaFile -Raw
if ($CurrentSchema -notmatch 'SCHEMA_VERSION\s*=\s*2') {
    throw "The project does not look like MinePilot recorder schema v2 stage 1."
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ProjectParent = Split-Path -Parent $ProjectRoot
$BackupRoot = Join-Path $ProjectParent "mc_ai_recorder_backup_v2_stage2_$Timestamp"

Write-Host ""
Write-Host "MinePilot Mod recorder schema-v2 stage 2" -ForegroundColor Cyan
Write-Host "Project: $ProjectRoot"
Write-Host "Backup:  $BackupRoot"
Write-Host ""

New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null

$PayloadFiles = Get-ChildItem -Path $PayloadRoot -Recurse -File

foreach ($PayloadFile in $PayloadFiles) {
    $RelativePath = $PayloadFile.FullName.Substring($PayloadRoot.Length).TrimStart('\', '/')
    $TargetPath = Join-Path $ProjectRoot $RelativePath
    $BackupPath = Join-Path $BackupRoot $RelativePath

    if (Test-Path $TargetPath -PathType Leaf) {
        $BackupDirectory = Split-Path -Parent $BackupPath
        New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
        Copy-Item -LiteralPath $TargetPath -Destination $BackupPath -Force
    }

    $TargetDirectory = Split-Path -Parent $TargetPath
    New-Item -ItemType Directory -Force -Path $TargetDirectory | Out-Null
    Copy-Item -LiteralPath $PayloadFile.FullName -Destination $TargetPath -Force

    Write-Host "Installed: $RelativePath"
}

Write-Host ""
Write-Host "Building the Fabric mod..." -ForegroundColor Cyan

Push-Location $ProjectRoot
try {
    & .\gradlew.bat clean build

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$Jar = Join-Path $ProjectRoot "build\libs\mc_ai_recorder-0.6.0.jar"

Write-Host ""
Write-Host "Recorder schema-v2 stage 2 installed successfully." -ForegroundColor Green
Write-Host "Backup: $BackupRoot"

if (Test-Path $Jar) {
    $Hash = Get-FileHash -LiteralPath $Jar -Algorithm SHA256
    Write-Host "JAR:    $Jar"
    Write-Host "SHA256: $($Hash.Hash)"
}
else {
    Write-Warning "Build completed, but expected JAR was not found at: $Jar"
    Write-Host "Available JAR files:"
    Get-ChildItem (Join-Path $ProjectRoot "build\libs") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Select-Object Name, Length, LastWriteTime |
        Format-Table -AutoSize
}

Write-Host ""
Write-Host "Next:"
Write-Host "  1. Copy mc_ai_recorder-0.6.0.jar to the Prism Launcher mods directory."
Write-Host "  2. Record the stage-2 acceptance scenario."
Write-Host "  3. Validate container_snapshots.jsonl and game_events.jsonl on Linux."

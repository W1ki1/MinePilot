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

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ProjectParent = Split-Path -Parent $ProjectRoot
$BackupRoot = Join-Path $ProjectParent "mc_ai_recorder_backup_v2_stage1_$Timestamp"

Write-Host ""
Write-Host "MinePilot Mod recorder schema-v2 stage 1"
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
Write-Host "Building the Fabric mod..."

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

$Jar = Join-Path $ProjectRoot "build\libs\mc_ai_recorder-0.5.0.jar"

Write-Host ""
Write-Host "Recorder schema-v2 stage 1 installed successfully."
Write-Host "Backup: $BackupRoot"

if (Test-Path $Jar) {
    Write-Host "JAR:    $Jar"
}
else {
    Write-Warning "Build completed, but expected JAR was not found at: $Jar"
}

Write-Host ""
Write-Host "Next:"
Write-Host "  1. Copy the non-sources JAR to the Prism Launcher mods directory."
Write-Host "  2. Record a short episode."
Write-Host "  3. Copy the episode to Linux and run the backend validator."

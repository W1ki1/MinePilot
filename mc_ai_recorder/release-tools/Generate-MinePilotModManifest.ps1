param(
    [string]$ProjectRoot = "G:\MinePilot\MinePilot\mc_ai_recorder",
    [string]$OutputPath = "",
    [switch]$RequireClean
)

$ErrorActionPreference = "Stop"

$ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)

if (-not (Test-Path $ProjectRoot -PathType Container)) {
    throw "Project directory does not exist: $ProjectRoot"
}

$GradleProperties = Join-Path $ProjectRoot "gradle.properties"
$Jar = Join-Path $ProjectRoot "build\libs\mc_ai_recorder-1.0.0.jar"

if (-not (Test-Path $GradleProperties -PathType Leaf)) {
    throw "gradle.properties does not exist."
}

if (-not (Test-Path $Jar -PathType Leaf)) {
    throw "Final 1.0.0 JAR does not exist: $Jar"
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $ProjectRoot "release\minepilot-mod-1.0.0.manifest.json"
}

$Properties = @{}

foreach ($Line in Get-Content -LiteralPath $GradleProperties) {
    $Trimmed = $Line.Trim()

    if (
        [string]::IsNullOrWhiteSpace($Trimmed) -or
        $Trimmed.StartsWith("#") -or
        -not $Trimmed.Contains("=")
    ) {
        continue
    }

    $Key, $Value = $Trimmed.Split("=", 2)
    $Properties[$Key.Trim()] = $Value.Trim()
}

$Commit = (& git -C $ProjectRoot rev-parse HEAD).Trim()
$Branch = (& git -C $ProjectRoot rev-parse --abbrev-ref HEAD).Trim()
$StatusLines = @(& git -C $ProjectRoot status --porcelain)
$Dirty = $StatusLines.Count -gt 0

if ($LASTEXITCODE -ne 0) {
    throw "Unable to read Git state."
}

if ($RequireClean -and $Dirty) {
    throw "Repository is dirty. Commit or stash changes first."
}

$JarInfo = Get-Item -LiteralPath $Jar
$JarHash = Get-FileHash -LiteralPath $Jar -Algorithm SHA256

$Manifest = [ordered]@{
    manifestVersion = 1
    generatedAtUtc = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    status = if ($Dirty) { "candidate" } else { "training-ready" }
    releaseId = "MinePilot-Mod-1.0.0"
    modVersion = $Properties["mod_version"]
    minecraftVersion = $Properties["minecraft_version"]
    fabricLoaderVersion = $Properties["loader_version"]
    fabricApiVersion = $Properties["fabric_api_version"]
    loomVersion = $Properties["loom_version"]
    javaVersion = "25"
    protocolVersion = 2
    schemaVersion = 2
    capabilities = @(
        "OBSERVATION_JPEG",
        "ACTION_SAFE_IDLE",
        "ACTION_WORLD",
        "ACTION_GUI",
        "HEARTBEAT"
    )
    git = [ordered]@{
        commit = $Commit
        branch = $Branch
        dirty = $Dirty
    }
    artifact = [ordered]@{
        path = $JarInfo.FullName
        fileName = $JarInfo.Name
        sizeBytes = $JarInfo.Length
        sha256 = $JarHash.Hash.ToLowerInvariant()
    }
}

$OutputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$Manifest |
    ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath $OutputPath -Encoding UTF8

Write-Host ""
Write-Host "MinePilot mod release manifest generated." -ForegroundColor Green
Write-Host "Status:  $($Manifest.status)"
Write-Host "Commit:  $Commit"
Write-Host "JAR:     $Jar"
Write-Host "SHA256:  $($Manifest.artifact.sha256)"
Write-Host "Manifest: $OutputPath"

if ($Manifest.status -ne "training-ready") {
    exit 1
}

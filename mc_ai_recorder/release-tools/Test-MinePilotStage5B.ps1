param(
    [string]$ProjectRoot = "G:\MinePilot\MinePilot\mc_ai_recorder",
    [string]$BackendHost = "192.168.0.11",
    [int]$BackendPort = 5005
)

$ErrorActionPreference = "Stop"
$Errors = New-Object System.Collections.Generic.List[string]

function Check([bool]$Condition, [string]$Message) {
    if ($Condition) {
        Write-Host "[OK] $Message" -ForegroundColor Green
    }
    else {
        Write-Host "[ERROR] $Message" -ForegroundColor Red
        $script:Errors.Add($Message)
    }
}

$Jar = Join-Path $ProjectRoot "build\libs\mc_ai_recorder-1.0.0.jar"
$GradleProperties = Join-Path $ProjectRoot "gradle.properties"
$Client = Join-Path $ProjectRoot "src\client\java\pl\pixeloza\mc_ai_recorder\client\inference\ProtocolV2Client.java"
$RuntimeConfig = Join-Path $ProjectRoot "src\client\java\pl\pixeloza\mc_ai_recorder\client\config\RuntimeConfig.java"
$ManifestTool = Join-Path $ProjectRoot "release-tools\Generate-MinePilotModManifest.ps1"

Check (Test-Path $Jar -PathType Leaf) "1.0.0 JAR exists"
Check (Test-Path $GradleProperties -PathType Leaf) "gradle.properties exists"
Check (Test-Path $Client -PathType Leaf) "ProtocolV2Client.java exists"
Check (Test-Path $RuntimeConfig -PathType Leaf) "RuntimeConfig.java exists"
Check (Test-Path $ManifestTool -PathType Leaf) "Release manifest generator exists"

if (Test-Path $GradleProperties -PathType Leaf) {
    $Text = Get-Content -LiteralPath $GradleProperties -Raw
    Check ($Text -match '(?m)^mod_version=1\.0\.0$') "Final mod version is 1.0.0"
    Check ($Text -match '(?m)^minecraft_version=26\.1\.2$') "Minecraft version remains 26.1.2"
}

if (Test-Path $Client -PathType Leaf) {
    $Text = Get-Content -LiteralPath $Client -Raw
    Check ($Text -match '"1\.0\.0"') "HELLO client version is 1.0.0"
    Check ($Text -notmatch '"0\.11\.0"') "RC client version is absent"
    Check ($Text -match "validateServerCapabilities") "Backend capabilities remain enforced"
}

if (Test-Path $RuntimeConfig -PathType Leaf) {
    $Text = Get-Content -LiteralPath $RuntimeConfig -Raw
    Check ($Text -match "minepilot-runtime.json") "External runtime configuration remains enabled"
    Check ($Text -match "requireServerCapabilities") "Strict compatibility remains enabled"
}

if (Test-Path $Jar -PathType Leaf) {
    $Hash = Get-FileHash -LiteralPath $Jar -Algorithm SHA256
    Check ($Hash.Hash.Length -eq 64) "JAR SHA-256 can be calculated"
}

$Connection = Test-NetConnection $BackendHost -Port $BackendPort -WarningAction SilentlyContinue
Check ($Connection.TcpTestSucceeded) "Backend TCP port is reachable"

Write-Host ""
if ($Errors.Count -eq 0) {
    Write-Host "STAGE 5B MOD PREFLIGHT: PASS" -ForegroundColor Green
    exit 0
}

Write-Host "STAGE 5B MOD PREFLIGHT: FAIL" -ForegroundColor Red
exit 1

param(
    [string]$DatasetRoot = "G:\MinecraftAI\Recordings\minepilot_v2",
    [string]$EpisodePath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($EpisodePath)) {
    $EpisodePath = Get-ChildItem (Join-Path $DatasetRoot "episodes") -Directory |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

Write-Host "Episode: $EpisodePath" -ForegroundColor Cyan

$Required = @(
    "metadata.json",
    "actions.jsonl",
    "gui_actions.jsonl",
    "inventory_snapshots.jsonl",
    "world_snapshots.jsonl",
    "container_snapshots.jsonl",
    "game_events.jsonl"
)

$Errors = 0
foreach ($Name in $Required) {
    $Path = Join-Path $EpisodePath $Name
    if (Test-Path -LiteralPath $Path) {
        Write-Host "[ OK ] $Name" -ForegroundColor Green
    }
    else {
        Write-Host "[ERR ] Missing $Name" -ForegroundColor Red
        $Errors++
    }
}

$ContainerPath = Join-Path $EpisodePath "container_snapshots.jsonl"
$EventsPath = Join-Path $EpisodePath "game_events.jsonl"

if (Test-Path $ContainerPath) {
    $ContainerRecords = @(Get-Content $ContainerPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    Write-Host "Container snapshots: $($ContainerRecords.Count)"
    if ($ContainerRecords.Count -eq 0) {
        Write-Host "[ERR ] No container snapshots" -ForegroundColor Red
        $Errors++
    }
}

if (Test-Path $EventsPath) {
    $EventRecords = @(Get-Content $EventsPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    Write-Host "Game events: $($EventRecords.Count)"
    if ($EventRecords.Count -eq 0) {
        Write-Host "[ERR ] No game events" -ForegroundColor Red
        $Errors++
    }
    else {
        Write-Host "Event types:" -ForegroundColor Cyan
        $EventRecords |
            ForEach-Object { $_ | ConvertFrom-Json } |
            Group-Object eventType |
            Sort-Object Count -Descending |
            Format-Table Count, Name -AutoSize
    }
}

if ($Errors -gt 0) {
    Write-Host "STAGE 2 WINDOWS CHECK: FAIL" -ForegroundColor Red
    exit 1
}

Write-Host "STAGE 2 WINDOWS CHECK: PASS" -ForegroundColor Green

# MinePilot Mod — Recorder schema v2, stage 1

This bundle updates only the **MinePilot-Mod** repository.

## Included

- schema-v2 `metadata.json`
- schema-v2 `actions.jsonl`
- `inventory_snapshots.jsonl`
- `world_snapshots.jsonl`
- schema-v2 raw `gui_actions.jsonl`
- global per-episode `sequenceId`
- semantic `screenType`, `menuType`, and GUI slot roles
- target block/entity state
- nearby entity summaries
- player health, hunger, saturation, combat, movement, and status-effect state
- local sparse world snapshot: 11×7×11
- basic registry export and SHA-256 registry hash
- PNG frames at 224×224
- new dataset root:
  `G:\MinecraftAI\Recordings\minepilot_v2`

This is stage 1. It intentionally does not yet implement:

- `container_snapshots.jsonl`
- `game_events.jsonl`
- TCP protocol v2
- action executor v2
- final registry recipe/tag export

## Required backend compatibility patch

Before validating real episodes, apply on Linux:

```bash
chmod +x fix_backend_gui_raw_contract_v2.sh
./fix_backend_gui_raw_contract_v2.sh /opt/ai/mc-ai-bot
```

The patch lets raw GUI records preserve technical action names such as `OPEN`,
`LEFT_RELEASE`, `SCROLL_UP`, and `DRAG_OUTSIDE`. Strict GUI action classes remain
in the normalized dataset and runtime action contract.

## Install

From PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass

cd <folder containing this bundle>

.\apply_mod_recorder_v2_stage1.ps1 `
  -ProjectRoot "G:\MinePilot\MinePilot\mc_ai_recorder"
```

The installer:

1. creates a timestamped backup next to the project,
2. copies only changed/new files,
3. runs `gradlew.bat clean build`.

Expected JAR:

```text
G:\MinePilot\MinePilot\mc_ai_recorder\build\libs\mc_ai_recorder-0.5.0.jar
```

## First acceptance recording

Record a short episode that contains:

1. 5–10 seconds of walking and camera movement,
2. one hotbar change,
3. opening the inventory,
4. one left click and one right click,
5. closing the inventory,
6. stopping recording.

Expected output:

```text
G:\MinecraftAI\Recordings\minepilot_v2\
├── registries\
│   └── registry_<hash>.json
└── episodes\
    └── episode_YYYYMMDD_HHMMSS\
        ├── metadata.json
        ├── frames\
        ├── actions.jsonl
        ├── gui_actions.jsonl
        ├── inventory_snapshots.jsonl
        └── world_snapshots.jsonl
```

## Git

Run Git commands from the MinePilot-Mod repository root:

```powershell
cd G:\MinePilot\MinePilot

git status
git add mc_ai_recorder
git commit -m "Add recorder schema v2 world and inventory snapshots"
git push
```

Do not commit generated `build`, `run`, recordings, or local backup directories.

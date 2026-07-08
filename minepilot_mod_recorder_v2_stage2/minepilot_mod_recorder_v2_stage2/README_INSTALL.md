# MinePilot Mod — Recorder V2 Stage 2

This patch must be applied after Recorder V2 Stage 1.

It adds:

- `container_snapshots.jsonl`
- `game_events.jsonl`
- `containerRevision` references in world and GUI records
- semantic container slot roles
- furnace progress properties
- inferred inventory, container, health, hunger, damage, consumption, block and combat events

## Install on Windows

```powershell
Set-ExecutionPolicy -Scope Process Bypass

cd G:\MinePilot\Patches\minepilot_mod_recorder_v2_stage2

.\apply_mod_recorder_v2_stage2.ps1 `
  -ProjectRoot "G:\MinePilot\MinePilot\mc_ai_recorder"
```

The installer creates a backup next to the project and runs:

```powershell
.\gradlew.bat clean build
```

Expected JAR:

```text
G:\MinePilot\MinePilot\mc_ai_recorder\build\libs\mc_ai_recorder-0.6.0.jar
```

## Acceptance recording

Record one episode containing:

1. walk and move the camera
2. open inventory and move an item
3. open a chest or barrel and transfer an item
4. open a crafting table
5. open a furnace, add fuel and an input item
6. place a block
7. break a block
8. take damage
9. eat food
10. stop recording normally

Expected new files:

```text
container_snapshots.jsonl
game_events.jsonl
```

Some event types are marked `INFERRED`. This is intentional and part of schema v2.

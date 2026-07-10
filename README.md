# MinePilot Fabric Client

This repository contains the Minecraft Fabric client for the MinePilot research
platform. It records demonstrations, captures runtime observations, connects to the
Python backend over Protocol V2 and safely executes returned WORLD, GUI or `SAFE_IDLE`
actions.

The Python training, evaluation, runtime and Studio control-plane code lives in the
separate [`W1ki1/MinePilot-Studio`](https://github.com/W1ki1/MinePilot-Studio)
repository.

## Current status — 2026-07-10

The client has completed an end-to-end live integration with `WORLD_V2_NAV_A` and the
first atomic backend skill:

```text
MOVE_FORWARD_UNTIL_BLOCKED_V1
```

In the verified test, the client:

1. connected to the Protocol V2 runtime,
2. streamed temporal frames and structured world observations,
3. received model-driven `FORWARD` actions,
4. moved until the model detected a wall,
5. received a terminal `SAFE_IDLE`,
6. stopped immediately when AI control was disabled with F10.

Verified run summary:

```text
FORWARD model decisions:     23
Stable stop observations:    2
Model inference errors:      0
Skill terminal result:       COMPLETE
```

## Repository layout

```text
mc_ai_recorder/    Fabric client project
```

Important client areas:

```text
mc_ai_recorder/src/client/java/pl/pixeloza/mc_ai_recorder/client/
├── McAiRecorderClient.java
├── control/                  safe action execution and key release
├── hud/                      in-game runtime/debug status
├── inference/                Protocol V2 TCP connection and inference loop
├── recording/                episode, frame, action and observation capture
└── protocol/                 Protocol V2 messages and codecs
```

Additional historical migration notes are available in:

```text
mc_ai_recorder/README_MinePilot_Backend.md
```

That document is retained as detailed project history; this root README describes the
current client/backend split and verified runtime state.

## Client responsibilities

The Fabric client is responsible for:

- recording demonstration episodes,
- capturing JPEG gameplay frames,
- collecting player, world, target, entity and GUI metadata,
- collecting a local player-relative `runtimeWorld` block snapshot,
- sending Protocol V2 observations to the backend,
- validating and applying time-limited actions,
- releasing controlled keys when AI control is disabled,
- exposing connection and control status in the HUD,
- failing safely when the backend disconnects or an action expires.

The client does not load PyTorch checkpoints or make model decisions locally. Model
inference and skill/planner logic run in `MinePilot-Studio` on the Linux backend.

## Runtime observation flow

```text
Minecraft client
    |
    +-- JPEG frame
    +-- player state
    +-- world state
    +-- target block/entity
    +-- nearby entities
    +-- GUI state
    +-- local runtimeWorld block grid
    |
    v
Protocol V2 TCP observation
    |
    v
MinePilot Python runtime
    |
    +-- behavior-cloning model
    +-- atomic skill safety layer
    |
    v
Protocol V2 action
    |
    +-- WORLD action
    +-- GUI action
    +-- SAFE_IDLE
```

## Minecraft controls

| Key | Action |
|---|---|
| `F8` | Start or stop demonstration recording |
| `F9` | Legacy live-frame export |
| `F10` | Enable or disable AI control; disabling releases controlled keys |
| `F12` | Enable or disable the Protocol V2 TCP inference connection |
| `H` | Show or hide the debug HUD |

Recommended runtime sequence:

```text
1. Start the Python backend on port 5005.
2. Enter a safe Minecraft test area.
3. Press F12 and wait for the connection.
4. Press F10 to allow backend control.
5. Press F10 immediately to stop AI control when needed.
```

## Building the mod

Current project requirements documented by the client project:

- Minecraft Java Edition 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.154.0+26.1.2
- Java 25
- Gradle wrapper included in `mc_ai_recorder`

From Windows PowerShell:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder
.\gradlew.bat clean build
```

The compiled JAR is created in:

```text
mc_ai_recorder\build\libs\
```

Copy the newest JAR that does not contain `sources` in its name into the Minecraft
instance `mods` directory. Keep only the intended current MinePilot JAR in that folder
to avoid loading an older build.

## Recording data

A recorded episode uses the `episode_*` convention and contains frames plus structured
action/state logs:

```text
episode_YYYYMMDD_HHMMSS/
├── frames/
├── actions.jsonl
├── gui_actions.jsonl
└── metadata.json
```

World recordings contain movement, camera, combat/use, hotbar and player/world context.
GUI recordings contain screen context, semantic slot information, clicks, keyboard
input, scrolling and drag sequences.

Large episodes and generated training artifacts are intentionally not stored in this
repository.

## Protocol and safety

The current live runtime uses Protocol V2.

Important safety behavior includes:

- actions are associated with the observation sequence that produced them,
- actions have a bounded `validForTicks` lifetime,
- unsupported or stale actions are rejected or allowed to expire,
- GUI and WORLD actions are separated,
- opening unsupported GUI contexts results in safe behavior,
- disabling AI control releases movement keys,
- F10 remains the immediate operator kill switch,
- loss of the backend connection cannot leave permanent movement input active.

The first verified backend skill currently allows only `FORWARD`; all other movement,
camera, combat, inventory and item-use actions are blocked by the skill safety envelope.

## Development workflow

Client-side changes should be validated with a clean Gradle build:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder
.\gradlew.bat clean build
```

Backend/runtime changes should be tested in the `MinePilot-Studio` repository:

```bash
cd /opt/ai/mc-ai-bot
conda activate minepilot-train-cpu
./scripts/test-all.sh
```

For live tests, use a flat enclosed area without lava, drops, mobs or valuable items,
and keep F10 available as the immediate stop control.

## Next milestones

The next backend skills planned for integration are:

```text
TURN_LEFT_V1
TURN_RIGHT_V1
```

Together with `MOVE_FORWARD_UNTIL_BLOCKED_V1`, they will form the first simple
obstacle-reactive navigation controller.

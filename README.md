# MinePilot Fabric Client

MinePilot is a Minecraft behavior-cloning and autonomous-agent research platform. This
repository contains the Minecraft Fabric client: the recorder, Protocol V2 transport,
runtime observation capture, action validation and safe WORLD/GUI execution layer.

The Python data, training, evaluation, runtime and web control-plane code lives in the
separate [`W1ki1/MinePilot-Studio`](https://github.com/W1ki1/MinePilot-Studio)
repository.

## Current status — 2026-07-12

The client supports the complete transport and execution boundary required by the current
MinePilot two-head architecture:

```text
WORLD policy checkpoint + GUI policy checkpoint
                    |
                    v
       MinePilot-Studio dual runtime router
                    |
             Protocol V2 TCP
                    |
                    v
          MinePilot Fabric client
```

The client does not load PyTorch models locally. It streams observations to the backend
and safely applies the returned `WORLD`, `GUI` or `SAFE_IDLE` action.

Implemented on `main`:

- mixed WORLD and GUI demonstration recording inside one episode,
- 224×224 JPEG runtime frames,
- player, world, target, entity and GUI observation metadata,
- `runtimeWorld`, `runtimeInventory` and `runtimeContainer` snapshots,
- container fingerprints and authoritative slot lists,
- `lastAppliedActionSequenceId` feedback to the backend,
- strict WORLD/GUI action routing,
- atomic GUI click, outside-click, quick-craft, button and close protocol support,
- bounded action lifetime and duplicate GUI envelope protection,
- fail-safe key release and the F10 operator kill switch,
- navigation debug data in the HUD, including selected direction and left/right scores.

The earlier live `WORLD_V2_NAV_A` wall test also verified the full observation → model →
action loop with `MOVE_FORWARD_UNTIL_BLOCKED_V1`: the client moved forward, stopped at
the obstacle and returned to terminal `SAFE_IDLE` without inference errors.

The Studio backend now also contains a dual WORLD + GUI runtime. Its code path is ready
for shadow and controlled live testing, but actual GUI behavior quality depends on the
trained GUI dataset and checkpoint.

## Repository responsibilities

| Repository | Responsibility |
|---|---|
| `W1ki1/MinePilot` | Fabric recorder, runtime observations, Protocol V2 client and safe action execution |
| `W1ki1/MinePilot-Studio` | Dataset preparation, training, evaluation, model registry, runtime policies and web Studio |

## Repository layout

```text
mc_ai_recorder/    Fabric client project
```

Important client areas:

```text
mc_ai_recorder/src/client/java/pl/pixeloza/mc_ai_recorder/client/
├── McAiRecorderClient.java
├── config/                   runtime endpoint and recorder configuration
├── control/                  WORLD action execution and controlled-key release
├── hud/                      in-game runtime and navigation debug status
├── inference/                Protocol V2 TCP client, router and GUI executor
├── recording/                episodes, frames, actions and structured snapshots
└── protocol/                 Protocol V2 messages and codecs
```

Protocol and recorder documentation is available under:

```text
mc_ai_recorder/docs/
```

Historical backend migration notes are retained in:

```text
mc_ai_recorder/README_MinePilot_Backend.md
```

## End-to-end runtime flow

```text
Minecraft client
    |
    +-- JPEG frame, 224×224
    +-- player/world/target/entity state
    +-- GUI screen and menu context
    +-- runtimeWorld local block snapshot
    +-- runtimeInventory snapshot
    +-- runtimeContainer slots, carried item and fingerprint
    +-- lastAppliedActionSequenceId
    |
    v
Protocol V2 OBSERVATION
    |
    v
MinePilot-Studio runtime
    |
    +-- GUI closed  -> WORLD policy
    +-- GUI open    -> GUI policy
    +-- invalid or uncertain context -> SAFE_IDLE
    |
    v
Protocol V2 ACTION
    |
    +-- WORLD
    +-- GUI
    +-- SYSTEM / SAFE_IDLE
    |
    v
Client-side validation and execution
```

The backend chooses the policy. The client remains authoritative for the actual Minecraft
screen, menu, slot set and container fingerprint immediately before execution.

## One episode can contain WORLD and GUI data

A single recording can include walking, opening a container, moving items, closing the
screen and continuing through the world. WORLD and GUI training are separated later by
MinePilot-Studio; the recorder does not require separate recording sessions.

Typical episode layout:

```text
episode_YYYYMMDD_HHMMSS/
├── frames/
├── actions.jsonl
├── gui_actions.jsonl
├── inventory_snapshots.jsonl
├── container_snapshots.jsonl
├── world_snapshots.jsonl
└── metadata.json
```

`actions.jsonl` contains the regular world timeline. `gui_actions.jsonl` contains semantic
GUI events such as slot clicks, outside clicks, drags, key actions and screen closure.
Snapshot files are revisioned and referenced by recorded events so the Studio builders can
produce immutable prepared datasets without modifying raw episodes.

Large recordings, prepared datasets and checkpoints are intentionally not stored in this
repository.

## Runtime observation contract

The current runtime observation contains:

- `protocolVersion=2` and `schemaVersion=2`,
- `controlEnabled`,
- `lastAppliedActionSequenceId`,
- player/world/target/entity/GUI state,
- `runtimeInventory`,
- player-relative `runtimeWorld`,
- `runtimeContainer` when a supported container screen is open,
- container slots, carried item and `fingerprint`,
- JPEG frame metadata for a 224×224 frame at quality 80.

WORLD models may downscale the shared frame internally. GUI Policy V1 consumes the native
224×224 runtime image.

## Action execution and routing

The client separates action domains:

```text
GUI closed -> WORLD actions may execute
GUI open   -> GUI actions may execute
```

A WORLD action is not allowed to drive movement through an open screen. A GUI action is
not executed without an active compatible container context.

GUI execution validates:

- observed `screenType` and `menuType`,
- creative inventory blocking,
- slot IDs against the current menu,
- `expectedContainerFingerprint`,
- valid click and quick-craft semantics,
- action envelope sequence IDs to avoid executing the same received action twice.

After an action is applied, its action-envelope sequence ID is returned in the next
observation as `lastAppliedActionSequenceId`.

## Minecraft controls

| Key | Action |
|---|---|
| `F8` | Start or stop demonstration recording |
| `F9` | Legacy live-frame export |
| `F10` | Enable or disable AI control; disabling releases controlled keys |
| `F12` | Enable or disable the Protocol V2 TCP connection |
| `H` | Show or hide the debug HUD |

Recommended live-test sequence:

```text
1. Start the MinePilot-Studio runtime on port 5005.
2. Enter a disposable, enclosed Minecraft test area.
3. Press F12 and wait for the Protocol V2 connection.
4. Keep GUI execution in backend shadow mode for the first test.
5. Press F10 to allow control.
6. Press F10 immediately whenever the agent must stop.
```

## Building the mod

Current project versions:

- Minecraft Java Edition `26.1.2`,
- Fabric Loader `0.19.3`,
- Fabric API `0.154.0+26.1.2`,
- mod version `1.1.0-alpha.1`,
- Java 25,
- included Gradle wrapper.

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
instance `mods` directory. Keep only the intended current MinePilot JAR in that directory
to avoid loading an older build.

## Protocol and safety

Protocol V2 safety behavior includes:

- every action is tied to the observation sequence that produced it,
- actions expire after a bounded `validForTicks`,
- stale, malformed or unsupported actions fail closed,
- GUI and WORLD domains are mutually exclusive,
- creative and unsupported GUI contexts are blocked,
- container fingerprints prevent execution against stale slot state,
- disabling AI control releases controlled movement keys,
- disconnects and inference failures cannot leave permanent movement input active,
- F10 remains the immediate operator kill switch.

## Current limitations

- The Fabric client is an execution boundary, not a planner or model host.
- Long-horizon goals such as “craft a pickaxe” must be implemented by the backend.
- GUI Policy V1 in Studio is an atomic imitation model; it predicts one operation from the
  current frame rather than an entire crafting plan.
- A dual runtime should be validated in GUI shadow mode before real clicks are enabled.
- Unsupported screens and missing/stale container state intentionally produce `SAFE_IDLE`.

## Development workflow

Validate client changes with a clean Gradle build:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder
.\gradlew.bat clean build
```

Validate backend/runtime integration in `MinePilot-Studio`:

```bash
cd /opt/ai/mc-ai-bot
conda activate minepilot-train-cpu
./scripts/test-all.sh
```

For live tests, use a flat disposable area without lava, drops, mobs or valuable items and
keep F10 available at all times.

# MinePilot Fabric Mod

Fabric client mod for **MinePilot** — a hybrid visual-semantic Minecraft AI agent.

The mod records gameplay data, streams runtime observations to the MinePilot backend and executes validated semantic WORLD and GUI actions.

Current release:

```text
MinePilot Mod:     1.0.0
Minecraft:         Java 26.1.2
Fabric Loader:     0.19.3
Fabric API:        0.154.0+26.1.2
Java:              25
Protocol:          V2
Schema:            V2
Status:            training-ready
```

## Features

- RGB frame capture
- player state capture
- world state capture
- nearby entity snapshots
- inventory snapshots
- container snapshots
- sparse local block grid
- semantic GUI context
- game event capture
- TCP Protocol V2 client
- reconnect and heartbeat
- safe WORLD action executor
- semantic GUI action executor
- stale fingerprint protection
- exact screen and menu validation
- creative GUI blocking
- HUD runtime telemetry
- configurable backend endpoint
- release manifest generation

## Controls

```text
H    toggle debug HUD
F8   toggle dataset recording
F9   legacy mode
F10  toggle AI control
F12  toggle TCP connection
```

Recommended runtime flow:

```text
1. Start backend
2. Launch Minecraft
3. Enter a world
4. Press F12
5. Confirm Protocol: READY
6. Press F10 only when action execution is intended
```

## Safety behavior

The mod is fail-safe by default:

- no action execution without F10,
- WORLD actions are blocked while a screen is open,
- unknown GUI context falls back to SAFE_IDLE,
- creative inventory execution is blocked,
- stale container fingerprints are rejected,
- duplicate action sequence IDs are ignored,
- invalid actions are rejected,
- controls are released on timeout,
- controls are released on disconnect,
- controls are released on protocol error.

Default action timeout:

```text
750 ms
```

## Requirements

- Minecraft Java 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.154.0+26.1.2
- Java 25
- Gradle wrapper included in the repository
- MinePilot Backend 2.0.0

## Build

From PowerShell:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder

.\gradlew.bat clean build
```

Final JAR:

```text
build\libs\mc_ai_recorder-1.0.0.jar
```

Copy the JAR into the Fabric instance `mods` directory.

Keep only one MinePilot mod version in the instance.

## Runtime configuration

On first launch the mod creates:

```text
.minecraft/config/minepilot-runtime.json
```

Default configuration:

```json
{
  "host": "192.168.0.11",
  "port": 5005,
  "inferEveryTicks": 2,
  "heartbeatIntervalMs": 1000,
  "actionTimeoutMs": 750,
  "reconnectIntervalMs": 1000,
  "connectTimeoutMs": 2000,
  "readTimeoutMs": 3500,
  "requireServerCapabilities": true
}
```

Restart Minecraft after editing the file.

### Important fields

```text
host                       backend IP or hostname
port                       backend TCP port
inferEveryTicks            observation cadence
heartbeatIntervalMs        heartbeat frequency
actionTimeoutMs            stale action timeout
reconnectIntervalMs        reconnect delay
connectTimeoutMs            TCP connect timeout
readTimeoutMs               socket read timeout
requireServerCapabilities  strict compatibility check
```

At 20 TPS:

```text
inferEveryTicks = 2
```

means approximately 10 observations per second.

## Protocol compatibility

The client requires the backend to accept:

```text
OBSERVATION_JPEG
ACTION_SAFE_IDLE
ACTION_WORLD
ACTION_GUI
HEARTBEAT
```

If the backend is incompatible, the client enters:

```text
ERROR_SAFE_IDLE
RECONNECTING
```

instead of executing actions.

## HUD

The debug HUD shows:

```text
TCP v2
Protocol state
backend endpoint
backend release ID
connection state
RTT
observation count
action count
current route
current action
last GUI result
```

Example:

```text
TCP v2: ON
Protocol: READY
Endpoint: 192.168.0.11:5005
Backend: SAFE_IDLE / MinePilot-Backend-2.0.0
Connected: true
RTT: 1 ms
Route: GUI
Action: GUI:CLICK_SLOT:QUICK_MOVE:S0
GUI result: EXECUTED
```

Possible GUI results:

```text
EXECUTED
DUPLICATE_IGNORED
STALE_FINGERPRINT
CONTEXT_MISMATCH
CREATIVE_BLOCKED
NO_CONTAINER_SCREEN
INVALID_ACTION
EXECUTION_ERROR
```

## Dataset recording

Default Windows dataset path:

```text
G:\MinecraftAI\Recordings\minepilot_v2
```

Default Linux dataset path:

```text
/opt/ai/datasets/minepilot_v2
```

Recording format:

```text
frames: PNG
capture rate: 20 Hz
seed: not recorded
chat: not recorded
```

Recorded semantic state includes:

- player state,
- inventory,
- container state,
- target block or entity,
- nearby entities,
- local sparse block grid,
- game events,
- GUI actions,
- WORLD actions.

## WORLD actions

Supported WORLD fields include:

```text
forward
back
left
right
jump
sneak
sprint
attack
use
inventoryToggle
hotbarTarget
yawDeltaDegrees
pitchDeltaDegrees
validForTicks
```

## GUI actions

Supported operations:

```text
CLICK_SLOT
CLICK_OUTSIDE
QUICK_CRAFT_START
QUICK_CRAFT_ADD_SLOT
QUICK_CRAFT_END
BUTTON_CLICK
CLOSE_SCREEN
```

Supported click types:

```text
PICKUP
QUICK_MOVE
SWAP
CLONE
THROW
PICKUP_ALL
```

Supported menus include:

```text
GenericContainerMenu
FurnaceMenu
BlastFurnaceMenu
SmokerMenu
HopperMenu
DispenserMenu
CraftingMenu
InventoryMenu
StonecutterMenu
```

Creative GUI execution is intentionally blocked.

## Preflight

Run with the backend online:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder

.\release-tools\Test-MinePilotStage5B.ps1 `
  -ProjectRoot "G:\MinePilot\MinePilot\mc_ai_recorder" `
  -BackendHost "192.168.0.11" `
  -BackendPort 5005
```

Expected result:

```text
STAGE 5B MOD PREFLIGHT: PASS
```

## Release manifest

After committing the final source and returning to a clean repository:

```powershell
.\release-tools\Generate-MinePilotModManifest.ps1 `
  -ProjectRoot "G:\MinePilot\MinePilot\mc_ai_recorder" `
  -RequireClean
```

Generated file:

```text
release\minepilot-mod-1.0.0.manifest.json
```

The manifest contains:

- Git commit,
- branch,
- mod version,
- Minecraft version,
- Fabric versions,
- Java version,
- Protocol V2,
- Schema V2,
- capabilities,
- final JAR path,
- final JAR SHA-256.

## Repository layout

```text
src/client/java/pl/pixeloza/mc_ai_recorder/client/
├── config/
├── control/
├── hud/
├── inference/
└── recording/

release-tools/
docs/
build/
gradle/
```

## Git workflow

Recommended branches:

```text
main
release/mod-1.0.0
```

Recommended tag:

```text
mod-training-ready-v1.0.0
```

## Project status

Completed:

- recorder Stage 2,
- Protocol V2 client,
- safe WORLD executor,
- semantic GUI executor,
- GUI matrix validation,
- carried stack fingerprinting,
- configurable runtime,
- strict capability checks,
- release manifest,
- training-ready freeze.

Next stage:

```text
Stage 6 — first WORLD imitation model
```

## License

Add the project license here before public release.

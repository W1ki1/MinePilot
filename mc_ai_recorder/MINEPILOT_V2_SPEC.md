# MinePilot V2 Platform Specification

> Stable data, runtime, and execution contracts for a training-ready Minecraft survival agent.

**Document status:** Draft 0.1  
**Target schema version:** `2`  
**Target protocol version:** `2`  
**Target platform milestone:** `training-ready`  
**Project components:** `MinePilot-Mod`, `MinePilot-Backend`

---

## 1. Purpose

MinePilot V2 must provide a stable platform for collecting demonstrations, training models, evaluating policies, and running an autonomous Minecraft survival agent without repeatedly changing the recorder, transport, or action executor.

The platform must support future model changes without requiring changes to the Minecraft mod. The mod is a versioned sensor, recorder, transport client, executor, and safety layer. The backend owns data processing, validation, models, planning, memory, inference, and evaluation.

The platform is considered training-ready only when it can record and execute every action needed for the Survival MVP acceptance scenarios defined in this document.

---

## 2. Survival MVP scope

The first complete MinePilot survival agent should be able to learn the following capabilities:

- move, sprint, sneak, jump, swim, and control the camera
- identify and approach visible targets
- break and place blocks
- collect dropped items
- gather wood, stone, coal, and basic ores
- use the hotbar and manage the player inventory
- craft basic blocks, tools, weapons, a crafting table, a furnace, and a chest
- store items in and retrieve items from containers
- place fuel and input into furnaces
- retrieve smelted output
- obtain, cook, and eat food
- plant and harvest basic crops
- attack hostile entities
- use defensive items such as a shield
- react to low health, low hunger, drowning, falling, and nearby threats
- die, respawn, and recover control safely
- maintain a high-level task state and remember useful locations

The first implementation only needs first-class GUI support for:

- player inventory
- crafting table
- generic containers such as chests
- furnace
- blast furnace
- smoker

The data and protocol contracts must allow adding more screens later without changing the framing protocol.

### 2.1 Explicitly out of scope for the first training-ready release

The following are not required before platform freeze:

- Nether and End progression
- villager trading
- enchanting
- brewing
- redstone automation
- advanced automated farms
- multiplayer communication
- reinforcement learning
- a learned high-level planner
- long-context Transformer or LSTM policies
- multi-agent coordination

These features may be added later at the backend policy level or through new optional screen adapters.

---

## 3. Design principles

1. **The mod must remain model-agnostic.**
2. **The backend must not depend on Minecraft screen pixel coordinates when a semantic slot ID exists.**
3. **Schemas and protocols are versioned and validated.**
4. **Unknown screens and stale actions always produce safe idle behavior.**
5. **Datasets are split by episode, never randomly by sample for real evaluation.**
6. **Raw recordings are immutable. Derived datasets may be regenerated.**
7. **Events, observations, and actions use stable IDs and monotonic sequence numbers.**
8. **Large or frequently changing state uses revisions and snapshots.**
9. **Models may change without changing the mod contracts.**
10. **Every critical survival interaction must be testable without a trained model.**

---

## 4. Component responsibilities

## 4.1 MinePilot-Mod

The mod is responsible for:

- frame capture
- world and player observation capture
- raw input recording
- GUI event recording
- inventory and container snapshots
- game-event recording
- TCP protocol framing
- applying backend actions
- GUI slot execution
- safety timeouts
- reconnect behavior
- releasing all held controls
- refusing unsafe actions on unsupported screens

The mod must not contain:

- survival strategy
- recipe planning
- navigation planning
- task selection
- model code
- learned policy logic
- persistent world memory

## 4.2 MinePilot-Backend

The backend is responsible for:

- schema parsing
- dataset validation
- episode loading
- normalization
- dataset reports
- train/validation/test splits
- model architectures
- training and evaluation
- checkpoint management
- inference
- policy routing
- skill selection
- task planning
- persistent memory
- metrics and experiment tracking

---

## 5. Versioning policy

### 5.1 Schema version

Every episode must contain:

```json
{
  "schemaVersion": 2
}
```

in `metadata.json`.

All JSONL records inherit the schema version from the episode metadata. A record must include `recordType`, but does not need to repeat `schemaVersion`.

The backend must reject unknown major schema versions.

### 5.2 Protocol version

Every runtime connection performs a version handshake.

Supported version:

```text
protocolVersion = 2
```

A client and server with incompatible protocol versions must not start inference.

### 5.3 Checkpoint version

Every model checkpoint contains:

```text
checkpointVersion = 2
```

Checkpoint compatibility is checked before model loading.

### 5.4 Compatibility rule

Within version 2:

- new optional fields may be added
- existing required fields may not be renamed or removed
- enum values may be added only when consumers handle unknown values safely
- semantic meaning and units may not change

Any incompatible change requires a new major version.

---

## 6. Episode directory format

A schema-v2 episode uses this structure:

```text
episode_YYYYMMDD_HHMMSS/
├── metadata.json
├── frames/
│   ├── 00000001.png
│   ├── 00000002.png
│   └── ...
├── actions.jsonl
├── gui_actions.jsonl
├── inventory_snapshots.jsonl
├── container_snapshots.jsonl
└── game_events.jsonl
```

Required files:

- `metadata.json`
- `frames/`
- `actions.jsonl`

Conditionally required:

- `gui_actions.jsonl` when GUI activity occurred
- `inventory_snapshots.jsonl` when inventory snapshots were emitted
- `container_snapshots.jsonl` when a supported container was opened
- `game_events.jsonl` when events were emitted

Empty JSONL files are allowed but must contain no malformed lines.

Raw episode files are immutable after recording is finalized.

Derived files such as normalized GUI interactions must be stored separately or use clearly derived names.

---

## 7. Common record fields

Every JSONL record must contain:

```json
{
  "recordType": "WORLD_STEP",
  "sequenceId": 1250,
  "tick": 4102,
  "timestampMs": 1783450000500
}
```

Field definitions:

| Field | Type | Meaning |
|---|---:|---|
| `recordType` | string | Stable record discriminator |
| `sequenceId` | integer | Strictly increasing within the episode stream |
| `tick` | integer | Minecraft client tick |
| `timestampMs` | integer | Unix epoch time in milliseconds |

Rules:

- `sequenceId` must never decrease within one file
- duplicate `sequenceId` values are invalid
- `tick` may repeat for multiple events
- `timestampMs` must not move backwards by more than an allowed clock tolerance
- runtime sequence IDs and dataset sequence IDs use the same monotonic semantics

---

## 8. Episode metadata

Example:

```json
{
  "schemaVersion": 2,
  "episodeId": "episode_20260707_221605",
  "recorderVersion": "0.5.0",
  "minecraftVersion": "26.1.2",
  "fabricLoaderVersion": "0.19.3",
  "startedAt": "2026-07-07T22:16:05+02:00",
  "endedAt": "2026-07-07T22:18:20+02:00",
  "status": "COMPLETE",
  "terminationReason": "USER_STOP",
  "frameFormat": "PNG",
  "frameWidth": 224,
  "frameHeight": 224,
  "captureEveryTicks": 1,
  "worldSeedRecorded": false,
  "tags": [
    "survival",
    "inventory",
    "crafting"
  ],
  "notes": ""
}
```

Required fields:

- `schemaVersion`
- `episodeId`
- `recorderVersion`
- `minecraftVersion`
- `startedAt`
- `status`
- `frameFormat`
- `frameWidth`
- `frameHeight`

Allowed `status` values:

```text
RECORDING
COMPLETE
INTERRUPTED
FAILED
```

Allowed `terminationReason` values:

```text
USER_STOP
CLIENT_SHUTDOWN
CLIENT_CRASH
IO_ERROR
UNKNOWN
```

An interrupted episode may still be valid if all present lines parse and referenced frames exist.

---

## 9. World step schema

`actions.jsonl` contains one `WORLD_STEP` record per captured training step.

Example:

```json
{
  "recordType": "WORLD_STEP",
  "sequenceId": 1250,
  "tick": 4102,
  "timestampMs": 1783450000500,
  "frame": "frames/00001250.png",

  "player": {
    "health": 18.0,
    "maxHealth": 20.0,
    "hunger": 14,
    "saturation": 2.4,
    "armor": 5,
    "air": 300,
    "experienceLevel": 3,
    "experienceProgress": 0.42,
    "position": {
      "x": 123.25,
      "y": 64.0,
      "z": -44.75
    },
    "velocity": {
      "x": 0.08,
      "y": 0.0,
      "z": -0.11
    },
    "yawDegrees": 120.5,
    "pitchDegrees": 4.2,
    "onGround": true,
    "inWater": false,
    "inLava": false,
    "fallDistance": 0.0,
    "sprinting": true,
    "sneaking": false,
    "usingItem": false,
    "statusEffects": []
  },

  "world": {
    "dimension": "minecraft:overworld",
    "biome": "minecraft:plains",
    "gameTime": 48231,
    "dayTime": 231,
    "raining": false,
    "thundering": false,
    "blockLight": 15,
    "skyLight": 15
  },

  "target": {
    "type": "BLOCK",
    "distance": 3.42,
    "blockId": "minecraft:oak_log",
    "blockFace": "NORTH",
    "blockPosition": {
      "x": 125,
      "y": 64,
      "z": -46
    },
    "blockProperties": {
      "axis": "y"
    },
    "entityId": null,
    "entityType": null,
    "entityRelativePosition": null
  },

  "nearbyEntities": [
    {
      "entityId": "session:8841",
      "entityType": "minecraft:zombie",
      "relativePosition": {
        "x": 4.2,
        "y": 0.1,
        "z": -3.7
      },
      "distance": 5.60,
      "health": 20.0,
      "hostile": true,
      "alive": true
    }
  ],

  "inventoryRevision": 23,
  "containerRevision": null,

  "gui": {
    "open": false,
    "screenType": "NONE",
    "menuType": "NONE"
  },

  "action": {
    "forward": true,
    "back": false,
    "left": false,
    "right": false,
    "jump": false,
    "sneak": false,
    "sprint": true,
    "attack": true,
    "use": false,
    "inventoryToggle": false,
    "dropOne": false,
    "dropStack": false,
    "swapHands": false,
    "hotbarTarget": null,
    "yawDeltaDegrees": -0.8,
    "pitchDeltaDegrees": 0.1
  }
}
```

## 9.1 Player state

Required player fields:

```text
health
maxHealth
hunger
saturation
armor
air
position
velocity
yawDegrees
pitchDegrees
onGround
inWater
inLava
fallDistance
sprinting
sneaking
usingItem
statusEffects
```

A status effect entry:

```json
{
  "effectId": "minecraft:regeneration",
  "amplifier": 0,
  "durationTicks": 120
}
```

## 9.2 World state

Required world fields:

```text
dimension
biome
gameTime
dayTime
raining
thundering
blockLight
skyLight
```

## 9.3 Target state

Allowed target types:

```text
BLOCK
ENTITY
MISS
```

For `BLOCK`, include:

- `blockId`
- `blockFace`
- `blockPosition`
- `blockProperties`
- `distance`

For `ENTITY`, include:

- `entityId`
- `entityType`
- `entityRelativePosition`
- `distance`

Fields not applicable to the current target type must be `null`.

`blockProperties` is a string-to-string map. It must preserve properties useful for survival behavior, including crop age and block orientation.

## 9.4 Nearby entities

The recorder emits a bounded list of nearby entities.

Initial limits:

```text
maximum entries: 16
maximum radius: configurable, default 16 blocks
sort order: nearest first
```

Required fields per entity:

```text
entityId
entityType
relativePosition
distance
hostile
alive
```

`health` may be `null` when not available.

`entityId` is session-local. Policies must not assume it remains stable after reconnect or reload.

## 9.5 World action semantics

Boolean fields describe the demonstrated control state for that step.

Camera units are degrees:

```text
positive yawDeltaDegrees   = turn right
negative yawDeltaDegrees   = turn left
positive pitchDeltaDegrees = look down
negative pitchDeltaDegrees = look up
```

`hotbarTarget` semantics:

```text
null = no hotbar selection action
0–8  = select Minecraft hotbar slot index
```

The model encoder may map this to ten classes internally, but the raw schema uses `null` or the real slot index.

World training must reject or mask steps where:

```text
gui.open == true
```

unless the action is explicitly a screen transition action supported by the training task.

---

## 10. Inventory snapshots

`inventory_snapshots.jsonl` is revision based.

A new snapshot is emitted when the player inventory changes or when recording starts.

Example:

```json
{
  "recordType": "INVENTORY_SNAPSHOT",
  "sequenceId": 1251,
  "tick": 4102,
  "timestampMs": 1783450000510,
  "revision": 23,
  "selectedHotbarSlot": 2,
  "slots": [
    {
      "role": "HOTBAR",
      "index": 0,
      "item": {
        "itemId": "minecraft:stone_pickaxe",
        "count": 1,
        "damage": 17,
        "maxDamage": 131,
        "customName": null,
        "enchantments": []
      }
    },
    {
      "role": "MAIN",
      "index": 0,
      "item": {
        "itemId": "minecraft:oak_log",
        "count": 12,
        "damage": null,
        "maxDamage": null,
        "customName": null,
        "enchantments": []
      }
    }
  ],
  "armor": {
    "head": null,
    "chest": null,
    "legs": null,
    "feet": null
  },
  "offhand": null
}
```

Allowed inventory slot roles:

```text
HOTBAR
MAIN
ARMOR
OFFHAND
```

Indices:

```text
HOTBAR: 0–8
MAIN:   0–26
```

Empty slots may be omitted or represented with `item: null`. The recorder must use one representation consistently. The recommended representation is to include all logical slots and use `item: null`.

Item stack fields:

```text
itemId
count
damage
maxDamage
customName
enchantments
```

An enchantment entry:

```json
{
  "enchantmentId": "minecraft:sharpness",
  "level": 2
}
```

The backend infers general item capabilities from `itemId`; the mod does not need to duplicate recipe or food registries in every snapshot.

---

## 11. GUI raw action schema

`gui_actions.jsonl` stores raw GUI interactions before backend normalization.

Example click:

```json
{
  "recordType": "GUI_ACTION",
  "sequenceId": 1300,
  "tick": 4200,
  "timestampMs": 1783450002500,
  "frame": "frames/00001300.png",

  "screenType": "InventoryScreen",
  "menuType": "InventoryMenu",
  "slotCount": 46,
  "containerRevision": 5,
  "inventoryRevision": 24,

  "screen": {
    "width": 960,
    "height": 540,
    "guiScale": 2
  },

  "eventType": "MOUSE_CLICK",
  "actionType": "RIGHT_CLICK",
  "interactionId": 12,

  "mouseButton": 1,
  "keyCode": null,
  "modifiers": {
    "shift": false,
    "control": false,
    "alt": false
  },

  "pointer": {
    "xPixels": 482.0,
    "yPixels": 261.0,
    "xNormalized": 0.5021,
    "yNormalized": 0.4833
  },

  "slot": {
    "slotId": 28,
    "role": "PLAYER_INVENTORY",
    "centerXNormalized": 0.505,
    "centerYNormalized": 0.481
  },

  "slotItemBefore": {
    "itemId": "minecraft:oak_planks",
    "count": 16
  },
  "slotItemAfter": {
    "itemId": "minecraft:oak_planks",
    "count": 8
  },
  "carriedItemBefore": null,
  "carriedItemAfter": {
    "itemId": "minecraft:oak_planks",
    "count": 8
  }
}
```

Required GUI context fields:

```text
screenType
menuType
slotCount
screen.width
screen.height
screen.guiScale
eventType
actionType
interactionId
pointer
slot
```

Allowed raw event types:

```text
SCREEN_OPEN
SCREEN_CLOSE
MOUSE_CLICK
MOUSE_RELEASE
KEY_PRESS
KEY_RELEASE
SCROLL
DRAG_START
DRAG_SLOT
DRAG_END
BUTTON_CLICK
```

Initial normalized action classes:

```text
LEFT_CLICK
RIGHT_CLICK
SHIFT_LEFT_CLICK
SHIFT_RIGHT_CLICK
LEFT_CLICK_OUTSIDE
RIGHT_CLICK_OUTSIDE
SHIFT_LEFT_CLICK_OUTSIDE
SHIFT_RIGHT_CLICK_OUTSIDE
DRAG_START_LEFT
DRAG_START_RIGHT
DRAG_SLOT_LEFT
DRAG_SLOT_RIGHT
DRAG_END_LEFT
DRAG_END_RIGHT
THROW_ONE
THROW_STACK
BUTTON_CLICK
CLOSE
```

The raw recorder must emit a `SCREEN_OPEN` event:

- when a screen is opened during recording
- immediately when recording begins while a screen is already open

Every drag sequence must use one `interactionId`.

---

## 12. Container snapshots

`container_snapshots.jsonl` stores the current semantic state of an open menu.

Example furnace snapshot:

```json
{
  "recordType": "CONTAINER_SNAPSHOT",
  "sequenceId": 1400,
  "tick": 4300,
  "timestampMs": 1783450004500,
  "revision": 5,

  "screenType": "FurnaceScreen",
  "menuType": "FurnaceMenu",
  "slotCount": 39,

  "source": {
    "type": "BLOCK",
    "blockId": "minecraft:furnace",
    "position": {
      "x": 130,
      "y": 64,
      "z": -40
    },
    "entityId": null
  },

  "slots": [
    {
      "slotId": 0,
      "role": "FURNACE_INPUT",
      "item": {
        "itemId": "minecraft:raw_iron",
        "count": 6
      }
    },
    {
      "slotId": 1,
      "role": "FURNACE_FUEL",
      "item": {
        "itemId": "minecraft:coal",
        "count": 3
      }
    },
    {
      "slotId": 2,
      "role": "FURNACE_OUTPUT",
      "item": null
    }
  ],

  "properties": {
    "burnTimeRemaining": 1180,
    "burnTimeTotal": 1600,
    "cookProgress": 72,
    "cookProgressTotal": 200
  }
}
```

Allowed semantic slot roles:

```text
HOTBAR
PLAYER_INVENTORY
ARMOR
OFFHAND
CRAFT_INPUT
CRAFT_OUTPUT
CONTAINER
FURNACE_INPUT
FURNACE_FUEL
FURNACE_OUTPUT
BLAST_FURNACE_INPUT
BLAST_FURNACE_FUEL
BLAST_FURNACE_OUTPUT
SMOKER_INPUT
SMOKER_FUEL
SMOKER_OUTPUT
UNKNOWN
```

Rules:

- `slotId` is the menu's semantic slot ID
- valid menu slots satisfy `0 <= slotId < slotCount`
- every visible logical menu slot should be represented
- unsupported or unknown roles use `UNKNOWN`
- unknown roles do not make the snapshot invalid
- model inference must mask slot IDs outside `slotCount`

Container properties are an extensible string-to-number-or-boolean map. Unknown properties must be preserved by parsers.

---

## 13. Game events

`game_events.jsonl` stores sparse state transitions and outcomes.

Example:

```json
{
  "recordType": "GAME_EVENT",
  "sequenceId": 1500,
  "tick": 4400,
  "timestampMs": 1783450006500,
  "eventType": "BLOCK_BROKEN",
  "source": "DIRECT",
  "data": {
    "blockId": "minecraft:oak_log",
    "position": {
      "x": 125,
      "y": 64,
      "z": -46
    }
  }
}
```

Allowed event sources:

```text
DIRECT
INFERRED
```

Required event types for the Survival MVP:

```text
BLOCK_BROKEN
BLOCK_PLACED
ITEM_PICKED_UP
ITEM_DROPPED
ITEM_CRAFTED
ITEM_SMELTED
ITEM_CONSUMED
CONTAINER_OPENED
CONTAINER_CLOSED
DAMAGE_RECEIVED
DAMAGE_DEALT
ENTITY_KILLED
PLAYER_DIED
PLAYER_RESPAWNED
HEALTH_CHANGED
HUNGER_CHANGED
INVENTORY_CHANGED
ADVANCEMENT
```

A backend parser must preserve unknown event types while warning about them.

Event payloads are type-specific. Every payload should contain the smallest useful semantic description and related IDs or positions when available.

Events may be inferred from state deltas when no direct client callback is available. Inferred events must use:

```text
source = INFERRED
```

---

## 14. Dataset validation requirements

The backend validator must detect:

- missing `metadata.json`
- unsupported `schemaVersion`
- malformed JSON
- duplicate or decreasing `sequenceId`
- missing referenced frames
- invalid frame dimensions
- invalid `hotbarTarget`
- invalid inventory slot indices
- `slotId < 0` when not explicitly an outside action
- `slotId >= slotCount`
- missing `SCREEN_OPEN` context
- unfinished drag sequences
- duplicate drag phases
- unknown critical enum values
- missing inventory revisions referenced by steps
- missing container revisions referenced by GUI records
- world training rows with `gui.open == true`
- timestamps that move backwards beyond tolerance
- episodes with no usable training rows
- incomplete episode status
- class imbalance warnings
- train/validation leakage by episode

Validation levels:

```text
ERROR   = episode or record cannot be used
WARNING = usable but suspicious
INFO    = dataset summary
```

The validator must support:

```bash
python -m minepilot.data.validate \
  --dataset /opt/ai/datasets/minepilot_v2
```

The report tool must support:

```bash
python -m minepilot.data.report \
  --dataset /opt/ai/datasets/minepilot_v2
```

---

## 15. Runtime protocol v2

## 15.1 Transport

Protocol v2 uses a binary envelope followed by JSON metadata and an optional binary payload.

Header:

```text
4 bytes  magic          ASCII "MPLT"
2 bytes  protocolVersion unsigned big-endian
1 byte   messageType
1 byte   flags
8 bytes  sequenceId     unsigned big-endian
4 bytes  metadataLength unsigned big-endian
4 bytes  payloadLength  unsigned big-endian
N bytes  UTF-8 JSON metadata
M bytes  binary payload
```

Initial message types:

```text
1 = HELLO
2 = HELLO_ACK
3 = OBSERVATION
4 = ACTION
5 = HEARTBEAT
6 = ERROR
7 = GOODBYE
```

Initial payload use:

```text
OBSERVATION payload = JPEG frame bytes
other messages      = empty unless explicitly extended
```

A receiver must reject:

- invalid magic
- unsupported protocol version
- impossible lengths
- oversized metadata
- oversized payloads
- truncated messages

Recommended configurable limits:

```text
metadata: 1 MiB
payload:  16 MiB
```

## 15.2 Handshake

Client sends `HELLO`:

```json
{
  "client": "MinePilot-Mod",
  "clientVersion": "1.0.0",
  "protocolVersion": 2,
  "minecraftVersion": "26.1.2",
  "capabilities": [
    "WORLD_ACTIONS",
    "GUI_ACTIONS",
    "INVENTORY_SNAPSHOTS",
    "CONTAINER_SNAPSHOTS",
    "GAME_EVENTS"
  ]
}
```

Server replies with `HELLO_ACK`:

```json
{
  "server": "MinePilot-Backend",
  "serverVersion": "2.0.0",
  "protocolVersion": 2,
  "accepted": true,
  "requiredCapabilities": [
    "WORLD_ACTIONS",
    "GUI_ACTIONS"
  ]
}
```

Inference starts only after a successful handshake.

## 15.3 Observation message

Observation metadata contains:

```text
sequenceId
tick
timestampMs
player
world
target
nearbyEntities
inventoryRevision
containerRevision
gui
lastAppliedActionSequenceId
```

The binary payload contains the JPEG frame.

Large inventory and container snapshots may be embedded when the corresponding revision changes or transmitted as optional snapshot fields in the observation metadata.

The exact transport representation may deduplicate unchanged snapshots, but the logical `ObservationV2` object exposed to policies must always resolve the current inventory and container state.

## 15.4 Action message

Example world action:

```json
{
  "actionType": "WORLD",
  "observationSequenceId": 9021,
  "validForTicks": 2,
  "confidence": 0.88,
  "world": {
    "forward": true,
    "back": false,
    "left": false,
    "right": false,
    "jump": false,
    "sneak": false,
    "sprint": true,
    "attack": false,
    "use": false,
    "inventoryToggle": false,
    "dropOne": false,
    "dropStack": false,
    "swapHands": false,
    "hotbarTarget": null,
    "yawDeltaDegrees": -0.4,
    "pitchDeltaDegrees": 0.1
  }
}
```

Example GUI action:

```json
{
  "actionType": "GUI",
  "observationSequenceId": 9022,
  "confidence": 0.94,
  "gui": {
    "action": "RIGHT_CLICK",
    "slotId": 7,
    "buttonId": null
  }
}
```

Example system action:

```json
{
  "actionType": "SYSTEM",
  "observationSequenceId": 9023,
  "system": {
    "action": "SAFE_IDLE"
  }
}
```

Allowed system actions:

```text
SAFE_IDLE
RELEASE_ALL_CONTROLS
RESPAWN
```

## 15.5 Stale action handling

The mod must reject an action when:

- its `observationSequenceId` is older than the latest acceptable observation
- it targets a screen or menu that is no longer open
- its `slotId` is invalid for the current `slotCount`
- its action type does not match the current router state
- it arrives after the configured inference timeout

---

## 16. Action executor requirements

## 16.1 World executor

The world executor must support:

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
dropOne
dropStack
swapHands
hotbarTarget
yawDeltaDegrees
pitchDeltaDegrees
```

Boolean movement controls are held until:

- a newer valid action changes them
- their `validForTicks` expires
- the inference timeout expires
- the connection is lost
- `RELEASE_ALL_CONTROLS` is applied

Camera deltas are applied once per accepted action.

## 16.2 GUI executor

The GUI executor must support:

```text
LEFT_CLICK
RIGHT_CLICK
SHIFT_LEFT_CLICK
SHIFT_RIGHT_CLICK
LEFT_CLICK_OUTSIDE
RIGHT_CLICK_OUTSIDE
SHIFT_LEFT_CLICK_OUTSIDE
SHIFT_RIGHT_CLICK_OUTSIDE
DRAG_START_LEFT
DRAG_START_RIGHT
DRAG_SLOT_LEFT
DRAG_SLOT_RIGHT
DRAG_END_LEFT
DRAG_END_RIGHT
THROW_ONE
THROW_STACK
BUTTON_CLICK
CLOSE
```

The executor uses semantic slot IDs, not model-provided raw pixels, whenever a slot action is requested.

Before executing a slot action, the mod verifies:

```text
screen is still open
menu identity still matches
0 <= slotId < slotCount
slot exists
action is allowed in current state
```

Unknown or unsupported screens must not receive automatic clicks.

## 16.3 Respawn executor

`RESPAWN` may only be accepted when the current screen/state is a recognized death state.

## 16.4 Safety behavior

The mod must release all controls when:

- TCP disconnects
- no valid action is received within the timeout
- the backend sends `RELEASE_ALL_CONTROLS`
- Minecraft changes to an unsupported screen
- the protocol parser fails
- the client exits the world

---

## 17. Policy routing contract

Router modes:

```text
WORLD
GUI
SAFE_IDLE
DEAD
DISCONNECTED
```

Routing rules:

```text
no screen open                     -> WORLD
supported container screen open   -> GUI
recognized death state            -> DEAD
unsupported or unknown screen     -> SAFE_IDLE
connection unavailable            -> DISCONNECTED
```

The router is deterministic and is not learned.

A backend policy must never override router safety rules.

Initial supported GUI contexts:

```text
InventoryScreen / InventoryMenu
CraftingScreen / CraftingMenu
GenericContainerScreen / GenericContainerMenu
FurnaceScreen / FurnaceMenu
BlastFurnaceScreen / BlastFurnaceMenu
SmokerScreen / SmokerMenu
```

Exact Java class names may differ by mappings or Minecraft version. The semantic `screenType` and `menuType` strings exposed by the mod must remain stable within schema version 2.

---

## 18. Backend policy API

The backend exposes stable logical interfaces.

```python
class Policy:
    def predict(self, observation: "ObservationV2") -> "ActionV2":
        ...
```

Required interfaces:

```text
Policy
WorldPolicy
GuiPolicy
PolicyRouter
SkillPolicy
TaskPlanner
MemoryStore
```

Suggested behavior:

```python
mode = router.select_mode(observation)

if mode == "WORLD":
    action = world_policy.predict(observation)
elif mode == "GUI":
    action = gui_policy.predict(observation)
elif mode == "DEAD":
    action = ActionV2.respawn()
else:
    action = ActionV2.safe_idle()
```

Model implementations may change without changing `ObservationV2` or `ActionV2`.

---

## 19. Planner and memory contract

The training-ready platform does not require a learned planner, but the backend must provide a stable planner boundary.

Suggested goal representation:

```json
{
  "goalType": "SURVIVE",
  "parameters": {}
}
```

Suggested skill IDs:

```text
EXPLORE
GATHER_WOOD
MINE_STONE
MINE_ORE
CRAFT_ITEM
PLACE_BLOCK
SMELT_ITEM
STORE_ITEMS
RETRIEVE_ITEMS
GET_FOOD
COOK_FOOD
EAT
PLANT_CROP
HARVEST_CROP
FIGHT
FLEE
RECOVER
```

Minimum memory domains:

```text
currentGoal
currentSkill
recentActions
recentEvents
knownLocations
knownContainers
containerContents
inventoryHistory
recentFailures
```

The mod does not store or interpret this memory.

---

## 20. Model contracts

## 20.1 World Policy V2

Input:

```text
4 RGB frames
player state
target state
nearby entity summary
selected inventory features
```

Initial visual stack:

```text
stack size: 4
frame stride: 4
image size: 224×224
```

Outputs:

```text
10 binary world controls
2 camera regression values
10-class hotbar action
```

Binary controls:

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
```

Additional discrete controls such as drop and swap may initially be separate heads or skill-controlled actions.

Hotbar model classes:

```text
0 = no change
1 = select raw hotbar slot 0
2 = select raw hotbar slot 1
...
9 = select raw hotbar slot 8
```

## 20.2 GUI Policy V2

Input:

```text
RGB frame
screenType
menuType
slotCount
container snapshot
inventory snapshot
```

Outputs:

```text
GUI action class
slot class
optional button ID
optional pointer prediction for diagnostics
```

The model must use a slot mask derived from `slotCount`.

Invalid-slot rate is a required evaluation metric.

## 20.3 High-level policies

Future skill and planner models may consume:

```text
current observation
recent event history
inventory state
known locations
current goal
current skill
```

They must produce skill or goal decisions, not direct Minecraft key states.

---

## 21. Checkpoint format

Required checkpoint content:

```python
{
    "checkpointVersion": 2,
    "modelType": "world_v2",
    "modelConfig": {},
    "datasetSchemaVersion": 2,
    "protocolVersion": 2,
    "actionNames": [],
    "screenTypes": [],
    "menuTypes": [],
    "epoch": 0,
    "globalStep": 0,
    "metrics": {},
    "modelStateDict": {},
    "optimizerStateDict": {},
    "schedulerStateDict": {},
    "createdAt": "",
    "gitCommit": ""
}
```

Inference must reject checkpoints with:

- unsupported `checkpointVersion`
- incompatible `modelType`
- incompatible action mappings
- missing required configuration
- incompatible schema assumptions

---

## 22. Training and evaluation rules

### 22.1 Dataset splits

Real evaluation must split by episode:

```text
train episodes
validation episodes
test episodes
```

Sample-level random splits are allowed only for explicit smoke tests.

### 22.2 Required world metrics

```text
per-button precision
per-button recall
per-button F1
camera MAE
camera percentile error
hotbar accuracy
inventory-toggle precision
inventory-toggle recall
event-conditioned success metrics
```

### 22.3 Required GUI metrics

```text
action accuracy
per-action precision
per-action recall
slot accuracy
joint action-and-slot accuracy
invalid-slot rate
metrics by screenType
metrics by menuType
drag-sequence success
```

### 22.4 Dataset report requirements

The report must include:

```text
episode count
usable frame count
world sample count
GUI interaction count
screen distribution
menu distribution
action distribution
hotbar distribution
event distribution
class imbalance warnings
missing revision warnings
invalid slot warnings
episode duration summary
```

---

## 23. Acceptance tests before platform freeze

Every scenario must be possible through a scripted backend without using a trained model.

For each scenario:

1. the mod records the complete interaction
2. the backend validator accepts the episode
3. the runtime protocol transmits the required observation
4. the executor can reproduce the required action
5. disconnect safety leaves no held keys

Required scenarios:

### A01 — Movement

- walk forward and backward
- strafe
- sprint
- sneak
- jump
- rotate camera

### A02 — Block interaction

- target a block
- break the block
- pick up the dropped item
- place a block

### A03 — Basic crafting

- open player inventory
- craft planks
- craft a crafting table
- place the crafting table
- open the crafting table
- craft a basic tool

### A04 — Inventory management

- move items between main inventory and hotbar
- split a stack
- shift-click an item
- perform a drag distribution
- drop one item
- drop a full stack

### A05 — Container management

- open a chest
- store an item
- retrieve an item
- close the chest
- preserve semantic slot roles

### A06 — Furnace workflow

- open a furnace
- insert fuel
- insert input
- observe progress fields
- retrieve output
- record `ITEM_SMELTED`

### A07 — Food

- obtain food
- consume food
- observe hunger change
- record `ITEM_CONSUMED`

### A08 — Farming

- identify farmland and crop state
- plant a crop
- identify a mature crop through block properties
- harvest the crop

### A09 — Combat and defense

- observe a nearby hostile entity
- target and attack it
- record damage events
- use a shield or defensive item
- record entity death when available

### A10 — Death and respawn

- record player death
- enter `DEAD` router state
- execute `RESPAWN`
- record player respawn
- resume safe control

### A11 — Connection safety

- disconnect while moving
- release all controls
- reconnect
- complete protocol handshake
- resume only after a fresh observation/action cycle

### A12 — Unsupported screen safety

- open an unsupported screen
- enter `SAFE_IDLE`
- refuse GUI clicks
- close manually without stuck controls

---

## 24. Freeze criteria

The platform may be tagged `training-ready` only when:

- schema v2 is implemented
- protocol v2 is implemented
- all required record files are produced
- validator passes all acceptance episodes
- dataset report works
- world executor supports all required world actions
- GUI executor supports semantic slot actions
- supported container snapshots are correct
- safety timeout and disconnect handling pass
- stale actions are rejected
- all acceptance tests A01–A12 pass
- backend policy interfaces are stable
- mod and backend documentation are updated
- both repositories have clean tagged releases

Target tags:

```text
MinePilot-Mod:     v1.0.0-training-ready
MinePilot-Backend: v2.0.0-training-ready
```

After freeze, permitted work should primarily include:

```text
new datasets
new model architectures
training
evaluation
skill policies
planners
memory systems
reinforcement learning
```

Any schema or protocol change after freeze requires a versioned migration plan.

---

## 25. Implementation order

1. Review and approve this specification.
2. Create schema dataclasses and validators in the backend.
3. Update the mod recorder to produce schema-v2 episodes.
4. Add inventory snapshots.
5. Add generic container snapshots.
6. Add required game events.
7. Implement protocol-v2 framing and handshake.
8. Implement world action executor v2.
9. Implement generic GUI executor v2.
10. Implement deterministic policy router.
11. Build scripted acceptance-test backend.
12. Run acceptance tests A01–A12.
13. Fix schema or execution gaps before freeze.
14. Tag both repositories as training-ready.
15. Begin large-scale data collection and model training.

---

## 26. Open decisions before approval

The following choices must be confirmed before implementation:

1. **Frame format:** keep PNG for datasets and JPEG for runtime transport.
2. **Capture rate:** default every client tick or a lower configurable rate.
3. **Nearby entity radius:** default 16 blocks and maximum 16 entities.
4. **Inventory snapshot representation:** include all logical slots with `item: null`.
5. **Container property names:** use stable MinePilot names rather than mapped Minecraft field names.
6. **Timeout defaults:** define inference timeout and heartbeat interval.
7. **Supported screen semantic names:** freeze names independently of mapping changes.
8. **Event implementation:** document which events are direct and which are inferred.
9. **World seed:** do not record by default.
10. **Raw world coordinates:** record for training and memory, with an option to disable for shareable datasets.

---

## 27. Security and privacy

Do not record:

- account access tokens
- server authentication secrets
- chat contents by default
- world seed by default
- external IP addresses
- private server credentials

Dataset metadata may include a server label but should not include credentials.

Runtime protocol v2 initially assumes a trusted private network. Authentication and encryption may be added through a secure tunnel or a later compatible transport layer without changing `ObservationV2` and `ActionV2`.

---

## 28. Final architectural boundary

The final boundary is:

```text
Minecraft
   │
   ▼
MinePilot-Mod
sensor + recorder + protocol + executor + safety
   │
   ▼
ObservationV2 / ActionV2
   │
   ▼
MinePilot-Backend
validation + policies + routing + planning + memory + training
```

A future model may be a ResNet, recurrent network, Transformer, multimodal policy, planner, or RL agent. As long as it consumes `ObservationV2` and produces `ActionV2`, the Minecraft mod does not need to change.

# MinePilot AI

> A custom Minecraft agent framework for recording demonstrations, training Behavior Cloning policies, and running real-time inference between Minecraft on Windows and a headless Linux server.

---

## Current status

MinePilot currently has two separate learning pipelines:

1. **World policy** — movement, camera control, attack, item use, sprinting, and other actions performed while no Minecraft screen is open.
2. **GUI policy** — inventory interaction, crafting, slot selection, shift-clicking, right-click item splitting, drag distribution, and closing Minecraft screens.

The project already supports:

- Fabric-based gameplay recording
- Episode-based datasets
- 224×224 RGB frame capture
- World action recording
- Hotbar-change recording
- Generic GUI state recording
- Inventory and crafting-screen detection
- Semantic slot IDs
- Item state before and after GUI actions
- Mouse click, release, keyboard, scroll, and drag recording
- GUI action normalization
- PyTorch datasets for world and GUI training
- ResNet18 Behavior Cloning models
- Four-frame world observation stacking
- Offline inference
- Real-time TCP frame transport
- Real-time Minecraft keyboard and camera control
- In-game debug HUD
- Model checkpoint saving
- Train/validation splitting by episode when multiple episodes are available

The GUI training pipeline has passed an end-to-end smoke test:

```text
raw gui_actions.jsonl
        ↓
gui_interactions.jsonl
        ↓
MinecraftGuiDataset
        ↓
ResNet18 GUI policy
        ↓
best_model.pt / last_model.pt
```

The current GUI checkpoint was trained only as a technical test on 33 samples. It is not yet suitable for autonomous gameplay.

---

## Architecture

### Recording and training

```text
Minecraft Java on Windows
        │
        ▼
Fabric recorder
        │
        ├── frames/
        ├── actions.jsonl
        ├── gui_actions.jsonl
        └── metadata.json
        │
        ▼
Dataset copied to Linux
        │
        ├── World dataset pipeline
        │       └── movement, camera, attack, use, hotbar
        │
        └── GUI normalization pipeline
                └── clicks, slots, drag sequences, crafting
```

### Planned runtime routing

```text
Minecraft frame + client state
        │
        ▼
Policy router
        │
        ├── client.screen == null
        │       └── World policy
        │
        └── client.screen != null
                └── GUI policy
```

Example autonomous crafting flow:

```text
World policy approaches crafting table
        │
        ▼
World policy predicts USE
        │
        ▼
CraftingScreen opens
        │
        ▼
Router switches to GUI policy
        │
        ▼
GUI policy places ingredients and collects output
        │
        ▼
GUI policy predicts CLOSE
        │
        ▼
Router switches back to world policy
```

The router and final GUI execution controller are not implemented yet.

---

## Requirements

### Windows

- Minecraft Java Edition 26.1.2
- Prism Launcher
- Fabric Loader 0.19.3
- Fabric API 0.154.0+26.1.2
- Java 25
- Gradle wrapper included in the Fabric project

### Linux

The Linux machine may be completely headless. No desktop environment, X11, Wayland, or monitor is required.

Current environment:

```bash
conda activate minerl-bot
```

Required Python packages include:

- Python
- PyTorch
- torchvision
- Pillow
- NumPy
- tqdm

Current development server:

```text
Linux:   192.168.0.11
Windows: 192.168.0.50
TCP:     5005
```

---

## Project layout

### Fabric mod

```text
mc-ai-recorder/
├── src/
│   ├── client/
│   │   └── java/
│   │       └── pl/pixeloza/mc_ai_recorder/client/
│   │           ├── McAiRecorderClient.java
│   │           ├── control/
│   │           │   └── AiController.java
│   │           ├── hud/
│   │           │   ├── AiDebugState.java
│   │           │   └── AiHud.java
│   │           ├── inference/
│   │           │   ├── AiAction.java
│   │           │   ├── InferenceClient.java
│   │           │   ├── TcpFrameCapture.java
│   │           │   └── TcpInferenceLoop.java
│   │           ├── legacy/
│   │           │   ├── ActionReader.java
│   │           │   └── LiveFrameExporter.java
│   │           └── recording/
│   │               ├── EpisodeMetadata.java
│   │               ├── FrameCapture.java
│   │               ├── GuiInteractionRecorder.java
│   │               ├── GuiInteractionSnapshot.java
│   │               ├── InputSnapshot.java
│   │               ├── JsonlWriter.java
│   │               └── RecordingManager.java
│   └── main/
│       └── resources/
│           └── fabric.mod.json
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew
└── gradlew.bat
```

### Current Linux layout

The Python project currently uses a temporary flat layout:

```text
/opt/ai/mc-ai-bot/
├── checkpoints/
├── checkpoints_stack/
├── checkpoints_gui_test/
├── tools/
└── training/
    ├── dataset.py
    ├── model.py
    ├── train_bc.py
    ├── inference.py
    ├── inference_server.py
    ├── live_inference.py
    ├── dataset_stack.py
    ├── model_stack.py
    ├── train_bc_stack.py
    ├── inference_stack.py
    ├── inference_server_stack.py
    ├── dataset_stack_inventory.py
    ├── train_bc_stack_inventory.py
    ├── inference_server_stack_inventory.py
    ├── normalize_gui_actions.py
    ├── dataset_gui.py
    ├── model_gui.py
    ├── train_gui.py
    └── world_sample_filter.py
```

This layout works, but it should be refactored before adding more models and runtime components.

### Proposed refactor

```text
/opt/ai/mc-ai-bot/
├── configs/
│   ├── world_v2.yaml
│   └── gui_v1.yaml
├── src/
│   └── minepilot/
│       ├── __init__.py
│       ├── common/
│       │   ├── checkpoints.py
│       │   ├── image_transforms.py
│       │   └── metrics.py
│       ├── world/
│       │   ├── dataset.py
│       │   ├── model.py
│       │   ├── train.py
│       │   ├── inference.py
│       │   ├── server.py
│       │   └── filters.py
│       ├── gui/
│       │   ├── normalize.py
│       │   ├── dataset.py
│       │   ├── model.py
│       │   ├── train.py
│       │   ├── inference.py
│       │   └── server.py
│       └── runtime/
│           ├── protocol.py
│           └── router.py
├── scripts/
├── checkpoints/
│   ├── world/
│   └── gui/
├── runs/
│   ├── world/
│   └── gui/
├── tests/
├── archive/
│   ├── single_frame_v1/
│   └── stack_inventory_experiment/
├── pyproject.toml
└── README.md
```

The refactor is planned but has not yet been applied.

---

## Building the Fabric mod

From Windows PowerShell:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder
.\gradlew.bat clean build
```

The compiled JAR is created in:

```text
build\libs\
```

Copy the JAR without `sources` in its filename into the Prism Launcher instance `mods` directory.

---

## Minecraft controls

| Key | Action |
|---|---|
| `F8` | Start or stop dataset recording |
| `F9` | Legacy live-frame export |
| `F10` | Enable or disable AI control |
| `F12` | Enable or disable TCP inference |
| `H` | Enable or disable the debug HUD |

Recommended live startup sequence:

```text
1. Start the Linux inference server
2. Press F12
3. Wait for the TCP connection
4. Press F10
```

Pressing `F10` again releases AI-controlled keys.

---

## Episode format

A current episode contains:

```text
episode_YYYYMMDD_HHMMSS/
├── frames/
├── actions.jsonl
├── gui_actions.jsonl
└── metadata.json
```

Use the `episode_*` naming convention. The GUI normalizer searches for this pattern by default.

### `actions.jsonl`

World-state records include:

- frame filename
- tick and timestamp
- movement keys
- jump, sneak, sprint, attack, and use
- yaw and pitch
- camera deltas
- player position
- health and food
- selected hotbar slot
- selected item
- hotbar-change event
- inventory toggle
- `inventoryOpen`
- `guiOpen`
- `screenType`
- dimension and biome
- movement state

Important GUI fields:

```json
{
  "inventory": false,
  "inventoryOpen": false,
  "guiOpen": true,
  "screenType": "CraftingScreen",
  "hotbarChanged": false,
  "hotbarTarget": -1
}
```

World training must reject samples where:

```python
guiOpen is True
```

### `gui_actions.jsonl`

Raw GUI records include:

- `screenType`
- event type
- semantic action type
- mouse button
- key code and modifiers
- mouse coordinates
- normalized mouse coordinates
- hovered slot ID
- slot coordinates
- item before and after the event
- carried item before and after the event
- drag movement
- `interactionId`

Example click:

```json
{
  "eventType": "MOUSE_CLICK",
  "actionType": "RIGHT_CLICK",
  "screenType": "InventoryScreen",
  "slotId": 28,
  "interactionId": 12
}
```

Example drag sequence:

```text
DRAG_START
DRAG_SLOT
DRAG_SLOT
DRAG_SLOT
DRAG_END
```

All events belonging to one drag use the same `interactionId`.

---

## Dataset location

Datasets are stored outside the code repository:

```text
/opt/ai/datasets/minecraft_custom
```

Example:

```text
/opt/ai/datasets/minecraft_custom/
├── episode_poruszaniesie_20260706_225231/
├── episode_scinaniedrewna_20260706_230133/
├── episode_crafting_20260706_231615/
└── episode_gui_inventory_20260707_221605/
```

---

# World policy

## Current baseline

The working world baseline uses four stacked RGB frames:

```text
stack_size = 4
frame_stride = 4
```

For an action at frame `100`, the model may receive:

```text
88, 92, 96, 100
```

Input shape:

```text
[12, 224, 224]
```

The 12 channels are created from:

```text
4 frames × 3 RGB channels
```

Current outputs:

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
yawDelta
pitchDelta
```

Button outputs use independent sigmoid probabilities. Camera output is continuous regression.

The current stack checkpoint is stored in:

```text
/opt/ai/mc-ai-bot/checkpoints_stack/
```

## Training

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python train_bc_stack.py \
  --dataset /opt/ai/datasets/minecraft_custom \
  --output /opt/ai/mc-ai-bot/checkpoints_stack \
  --epochs 5 \
  --batch-size 16 \
  --workers 4 \
  --stack-size 4 \
  --frame-stride 4
```

## Live inference

Stop any other service using TCP port `5005`, then run:

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python inference_server_stack.py
```

Minecraft sends one JPEG at a time. The Python server keeps the recent frame history required by the stack model.

## World model v2

The next world model should add:

### Inventory action

A binary output:

```text
inventory_toggle
```

### Hotbar action

A categorical output:

```text
0 = no hotbar change
1 = slot 1
2 = slot 2
...
9 = slot 9
```

A categorical hotbar head is preferred over nine independent booleans because only one slot can be selected at a time.

The existing `stack_inventory` files are experimental and should not become the final world architecture.

---

# GUI policy

## GUI normalization

Raw `gui_actions.jsonl` must be normalized before training:

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python normalize_gui_actions.py \
  --dataset /opt/ai/datasets/minecraft_custom
```

The script creates:

```text
episode_*/gui_interactions.jsonl
```

and a global index:

```text
/opt/ai/datasets/minecraft_custom/gui_interactions_index.jsonl
```

Normalization performs the following operations:

- combines a click and its release into one click interaction
- groups drag events by `interactionId`
- converts inventory and Escape key presses into `CLOSE`
- removes technical events that should not become direct policy targets
- preserves the frame and tick associated with every meaningful action

## Inspecting the GUI dataset

```bash
python dataset_gui.py \
  --dataset /opt/ai/datasets/minecraft_custom
```

Current action classes:

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
CLOSE
```

Slot encoding:

```text
0–127 = Minecraft slot ID
128   = outside all slots
129   = no slot
```

## GUI model

The smoke-test GUI model uses a ResNet18 image backbone and four heads:

```text
action head
slot head
screen-type auxiliary head
pointer head
```

Current input:

```text
[3, 224, 224]
```

Current output targets:

- action class
- slot class
- screen type
- normalized pointer position

The next version should use `screenType` as an explicit model input instead of only predicting it as an auxiliary target.

## GUI smoke test

The first technical test used:

```text
Samples: 33
Screen types:
  CraftingScreen
  InventoryScreen
```

Observed classes included:

```text
LEFT_CLICK
RIGHT_CLICK
SHIFT_LEFT_CLICK
DRAG_START_RIGHT
DRAG_SLOT_RIGHT
DRAG_END_RIGHT
CLOSE
```

The five-epoch CPU test confirmed that:

- image loading works
- labels are parsed correctly
- forward and backward passes work
- losses decrease
- checkpoints are saved
- the entire GUI pipeline works on a headless Linux server

The resulting checkpoint is only a pipeline test:

```text
/opt/ai/mc-ai-bot/checkpoints_gui_test/
```

It should not be used as a production policy.

## GUI smoke-test training command

```bash
python train_gui.py \
  --dataset /opt/ai/datasets/minecraft_custom \
  --output /opt/ai/mc-ai-bot/checkpoints_gui_test \
  --epochs 5 \
  --batch-size 8 \
  --num-workers 2
```

---

# TCP protocol

Default endpoint:

```text
host: 192.168.0.11
port: 5005
```

Request:

```text
4-byte big-endian JPEG length
JPEG bytes
```

Response:

```text
4-byte big-endian JSON length
JSON bytes
```

Current world response example:

```json
{
  "buttons": {
    "forward": true,
    "back": false,
    "left": false,
    "right": false,
    "jump": false,
    "sneak": false,
    "sprinting": true,
    "attack": false,
    "use": false
  },
  "camera": {
    "yawDelta": -0.42,
    "pitchDelta": 0.08
  }
}
```

The protocol must be extended later for:

- inventory toggle
- hotbar target
- GUI action
- GUI slot target
- GUI drag state
- policy-router state

---

# Debug HUD

The Fabric mod currently displays:

```text
TCP state
AI-control state
connection state
round-trip latency
JPEG size
movement actions
jump, sneak, and sprint
attack and use
yaw delta
pitch delta
```

Future HUD fields should include:

```text
active policy: WORLD / GUI
screenType
predicted GUI action
predicted slot
hotbar target
GUI confidence
```

---

# Data collection guidelines

## World demonstrations

Record varied examples of:

- walking
- sprinting
- strafing
- moving backward
- smooth camera movement
- jumping
- mining
- using blocks and items
- switching hotbar slots
- approaching targets
- correcting movement after collisions
- swimming
- exploration
- combat

Avoid excessive:

- AFK time
- repeated identical motion
- random camera spinning
- extremely long single episodes
- low-FPS recording
- GUI screens inside world-policy training data

## GUI demonstrations

Use many short episodes rather than one very long episode.

Record:

- left-click item movement
- right-click stack splitting
- shift-left-click
- shift-right-click
- closing with `E`
- closing with `Escape`
- left drag
- right drag
- inventory 2×2 crafting
- crafting-table 3×3 crafting
- collecting crafting output
- shift-clicking crafting output
- moving items between hotbar and inventory
- interacting with different slots
- different item counts
- different inventory layouts

Recommended first useful GUI dataset:

```text
10–20 separate episodes
2,000–5,000 atomic GUI samples
at least 50–100 examples of every important action
multiple recipes and item layouts
```

Training and validation should be split by episode.

---

# Immediate next steps

## 1. Refactor the Python repository

Move the flat scripts into the planned `src/minepilot/` package.

Archive, but do not delete:

```text
single-frame v1 files
stack-inventory experimental files
legacy inference files
```

Also remove Java source files and generated logs from the Python `training/` directory.

## 2. Build GUI model v2

Required changes:

- use `screenType` as an input embedding
- keep screen prediction only as an optional auxiliary task
- add slot masks based on the current screen
- ignore invalid slot classes during inference
- improve class-weight reporting
- add per-class precision, recall, and confusion matrices
- split train and validation strictly by episode

## 3. Build world model v2

Required outputs:

- existing world buttons
- camera regression
- inventory toggle
- categorical hotbar head

Reuse the current stack model backbone where possible.

## 4. Improve dataset tooling

Add:

- world/GUI episode summaries
- action-frequency reports
- duplicate-frame detection
- missing-class warnings
- split validation
- dataset version metadata
- schema version in every episode

## 5. Record a balanced GUI dataset

Before serious GUI training, collect enough examples for:

```text
LEFT_CLICK
RIGHT_CLICK
SHIFT_LEFT_CLICK
SHIFT_RIGHT_CLICK
LEFT_DRAG
RIGHT_DRAG
CLOSE
```

Record multiple inventory and crafting-table episodes with different items and layouts.

## 6. Train and evaluate both policies

World evaluation:

- per-button precision and recall
- camera MAE
- hotbar accuracy
- inventory-toggle precision and recall

GUI evaluation:

- action accuracy
- slot accuracy
- joint action-and-slot accuracy
- per-screen metrics
- drag-sequence success
- invalid-slot rate

## 7. Implement GUI inference

Add:

- `gui/inference.py`
- `gui/server.py`
- Java GUI executor
- slot-center lookup
- mouse-button press/release control
- drag-state execution
- close-screen execution

## 8. Implement the policy router

The runtime router should select:

```text
WORLD when no screen is open
GUI when a supported screen is open
SAFE_IDLE for unknown or unsupported screens
```

Unknown screens should not be clicked automatically.

## 9. Add higher-level task logic

After stable imitation learning:

- task state machine
- inventory awareness
- recipe and crafting planner
- navigation goals
- recovery from failed actions
- short-term memory
- recurrent or Transformer policy
- Behavior Cloning fine-tuning with reinforcement learning

---

# Roadmap

## Phase 1 — Recording

- [x] Fabric recorder
- [x] Frame capture
- [x] World action capture
- [x] Episode metadata
- [x] Hotbar-change capture
- [x] Generic GUI state capture
- [x] Slot-aware click capture
- [x] Drag capture
- [x] Separate `gui_actions.jsonl`

## Phase 2 — World Behavior Cloning

- [x] Single-frame baseline
- [x] ResNet18 model
- [x] Frame stacking
- [x] Keyboard prediction
- [x] Camera prediction
- [x] Offline inference
- [x] TCP live inference
- [ ] World model v2
- [ ] Inventory-toggle head
- [ ] Categorical hotbar head
- [ ] GUI-frame filtering in final dataset loader
- [ ] Per-action evaluation

## Phase 3 — GUI Behavior Cloning

- [x] GUI action normalizer
- [x] GUI PyTorch dataset
- [x] Slot classification
- [x] Drag-sequence representation
- [x] GUI model smoke test
- [x] Headless CPU training
- [ ] Screen-type input embedding
- [ ] Slot masking
- [ ] Balanced demonstration dataset
- [ ] Episode-level validation
- [ ] GUI offline inference
- [ ] GUI live inference
- [ ] Java GUI action executor

## Phase 4 — Runtime integration

- [x] TCP image transport
- [x] World keyboard and camera controller
- [x] Debug HUD
- [ ] Extended response protocol
- [ ] World/GUI policy router
- [ ] GUI action execution
- [ ] Safe handling of unsupported screens
- [ ] Runtime confidence thresholds

## Phase 5 — Autonomy

- [ ] Inventory-state model
- [ ] Recipe planner
- [ ] Crafting planner
- [ ] Navigation and mapping
- [ ] Goal planning
- [ ] Failure recovery
- [ ] Temporal memory
- [ ] Reinforcement Learning fine-tuning
- [ ] Multi-agent support

---

# License

MIT

---

# Author

Juliusz Wójcik  
Pixeloza

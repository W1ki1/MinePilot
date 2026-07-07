# MinePilot AI

> A custom Minecraft agent framework for recording demonstrations, training Behavior Cloning policies, and running real-time inference between Minecraft on Windows and a headless Linux server.

---

## Current status

MinePilot currently uses two separate learning pipelines:

1. **World policy** — movement, camera control, attack, item use, sprinting, hotbar selection, and other actions performed while no Minecraft screen is open.
2. **GUI policy** — inventory interaction, crafting, slot selection, shift-clicking, right-click item splitting, drag distribution, and closing Minecraft screens.

The project already supports:

- Fabric-based gameplay recording
- Episode-based datasets
- 224×224 RGB frame capture
- World action recording
- Hotbar-change recording
- Generic GUI-state recording
- Inventory and crafting-screen detection
- Semantic slot IDs
- Item state before and after GUI actions
- Mouse click, release, keyboard, scroll, and drag recording
- GUI action normalization
- PyTorch datasets for world and GUI training
- ResNet18 Behavior Cloning models
- Four-frame world observation stacking
- Offline world inference
- Real-time TCP frame transport
- Real-time Minecraft keyboard and camera control
- In-game debug HUD
- Model checkpoint saving
- Episode-based train/validation splitting when multiple episodes are available
- Headless Linux training through terminal or SSH
- Refactored Python package under `src/minepilot`
- Shell launchers under `scripts/`
- Archived legacy and experimental code
- Git-ready repository layout with large model files excluded

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

The current GUI checkpoint was trained only as a technical test on 33 samples. It is not suitable for autonomous gameplay yet.

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
        ├── supported screen is open
        │       └── GUI policy
        │
        └── unsupported screen is open
                └── SAFE_IDLE
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

The final policy router and Java GUI action executor are not implemented yet.

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

Current development endpoints:

```text
Linux:   192.168.0.11
Windows: 192.168.0.50
TCP:     5005
```

---

## Project structure

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

### Linux backend

The backend refactor has been completed.

```text
/opt/ai/mc-ai-bot/
├── archive/
│   ├── misplaced_java/
│   ├── single_frame_v1/
│   └── stack_inventory_experiment/
├── checkpoints/
│   ├── gui/
│   │   ├── migration_test/
│   │   └── smoke_test/
│   └── world/
│       ├── single_frame_v1/
│       └── stack_v1/
├── configs/
│   └── README.md
├── runs/
│   └── legacy/
├── scripts/
│   ├── inspect_gui_dataset.sh
│   ├── normalize_gui.sh
│   ├── serve_world.sh
│   ├── train_gui.sh
│   └── train_world.sh
├── src/
│   └── minepilot/
│       ├── __init__.py
│       ├── common/
│       │   └── __init__.py
│       ├── gui/
│       │   ├── __init__.py
│       │   ├── dataset.py
│       │   ├── model.py
│       │   ├── normalize.py
│       │   └── train.py
│       ├── runtime/
│       │   └── __init__.py
│       └── world/
│           ├── __init__.py
│           ├── dataset.py
│           ├── filters.py
│           ├── inference.py
│           ├── model.py
│           ├── server.py
│           └── train.py
├── tests/
│   └── __init__.py
├── .gitignore
├── pyproject.toml
└── README.md
```

Legacy code has been archived rather than deleted.

The refactor created compatibility links for older checkpoint paths where possible.

---

## Backend migration and backup

Before the Linux backend was restructured, a full project backup was created under:

```text
/opt/MinePilot-backedn-backup/mc-ai-bot_YYYYMMDD_HHMMSS
```

The completed migration also generated a local report:

```text
/opt/ai/mc-ai-bot/MIGRATION_REPORT.md
```

`MIGRATION_REPORT.md` is local-only and excluded from Git.

The backup contains the complete project state from before the migration and can be used for manual rollback.

Example rollback procedure:

```bash
sudo rm -rf /opt/ai/mc-ai-bot
sudo cp -a \
  /opt/MinePilot-backedn-backup/mc-ai-bot_YYYYMMDD_HHMMSS \
  /opt/ai/mc-ai-bot
sudo chown -R codex:codex /opt/ai/mc-ai-bot
```

Stop all training and inference processes before rollback.

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
└── episode_20260707_221605/
```

Datasets are intentionally not committed to Git.

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

Current world code:

```text
src/minepilot/world/
├── dataset.py
├── filters.py
├── inference.py
├── model.py
├── server.py
└── train.py
```

Current world checkpoint:

```text
checkpoints/world/stack_v1/
```

Compatibility path:

```text
checkpoints_stack
```

## Training

Run from the project root:

```bash
cd /opt/ai/mc-ai-bot
conda activate minerl-bot

./scripts/train_world.sh \
  --dataset /opt/ai/datasets/minecraft_custom \
  --output /opt/ai/mc-ai-bot/checkpoints/world/stack_v1 \
  --epochs 5 \
  --batch-size 16 \
  --workers 4 \
  --stack-size 4 \
  --frame-stride 4
```

## Live inference

Stop any other service using TCP port `5005`, then run:

```bash
cd /opt/ai/mc-ai-bot
conda activate minerl-bot

./scripts/serve_world.sh
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

The archived stack-inventory implementation is experimental and should not become the final world architecture.

---

# GUI policy

## GUI normalization

Raw `gui_actions.jsonl` must be normalized before training:

```bash
cd /opt/ai/mc-ai-bot
conda activate minerl-bot

./scripts/normalize_gui.sh
```

The script creates:

```text
episode_*/gui_interactions.jsonl
```

and a global index:

```text
/opt/ai/datasets/minecraft_custom/gui_interactions_index.jsonl
```

Normalization:

- combines a click and release into one click interaction
- groups drag events by `interactionId`
- converts inventory and Escape key presses into `CLOSE`
- removes technical events that should not become direct policy targets
- preserves the frame and tick associated with every meaningful action

## Inspecting the GUI dataset

```bash
./scripts/inspect_gui_dataset.sh
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

The current technical dataset contains:

```text
Samples: 33
Screen types:
  CraftingScreen
  InventoryScreen
```

Observed classes include:

```text
LEFT_CLICK
RIGHT_CLICK
SHIFT_LEFT_CLICK
DRAG_START_RIGHT
DRAG_SLOT_RIGHT
DRAG_END_RIGHT
CLOSE
```

The CPU smoke test confirmed that:

- image loading works
- labels are parsed correctly
- forward and backward passes work
- losses decrease
- checkpoints are saved
- the full GUI pipeline works on a headless Linux server
- the refactored package and launcher scripts work after migration

The smoke-test checkpoints are stored under:

```text
checkpoints/gui/smoke_test/
checkpoints/gui/migration_test/
```

They should not be used as production policies.

## GUI smoke-test training

```bash
./scripts/train_gui.sh \
  --dataset /opt/ai/datasets/minecraft_custom \
  --output /opt/ai/mc-ai-bot/checkpoints/gui/migration_test \
  --epochs 1 \
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

# Git workflow for the backend

## Repository

Recommended GitHub repository name:

```text
MinePilot-Backend
```

The repository should be private.

The local repository root is:

```text
/opt/ai/mc-ai-bot
```

Use Git as the `codex` user rather than `root`.

## Files intentionally excluded from Git

The current `.gitignore` excludes:

```text
minerl/
checkpoints/
checkpoints_stack
checkpoints_gui_test
runs/
*.pt
*.pth
*.ckpt
*.onnx
.env
.env.*
*.pem
*.key
__pycache__/
build/
dist/
MIGRATION_REPORT.md
```

This prevents GitHub from receiving:

- model checkpoints
- large third-party MineRL sources and build artifacts
- runtime logs
- local migration details
- Python caches
- local secrets
- environment files

Datasets stored in `/opt/ai/datasets/` are outside the repository and are not committed.

## First-time GitHub login on a headless server

```bash
su - codex
cd /opt/ai/mc-ai-bot

gh auth login \
  --hostname github.com \
  --git-protocol https \
  --web
```

GitHub CLI prints a temporary code. Open the displayed URL on another computer, log in to GitHub, and enter the code.

Then configure Git authentication:

```bash
gh auth setup-git
gh auth status
```

## Configure commit identity

```bash
git config --global user.name "Juliusz Wójcik"
git config --global user.email "YOUR_GITHUB_EMAIL"
```

Verify:

```bash
git config --global user.name
git config --global user.email
```

## Initialize the local repository

```bash
cd /opt/ai/mc-ai-bot

git init -b main
git add .
```

Inspect the staged files:

```bash
git status --short
git diff --cached --stat
```

Verify that large and private paths are not tracked:

```bash
git ls-files |
grep -E '(^minerl/|^checkpoints/|^runs/|\.pt$|\.pth$|\.jar$)' \
&& echo "ERROR: unwanted files are tracked" \
|| echo "OK: repository is clean"
```

Create the initial commit:

```bash
git commit -m "Initial MinePilot backend import"
```

## Create the private GitHub repository

```bash
gh repo create MinePilot-Backend \
  --private \
  --source=. \
  --remote=origin \
  --description "MinePilot training, inference and autonomous Minecraft agent backend" \
  --push
```

Do not use `--add-readme`, because this repository already contains `README.md`.

Verify:

```bash
git remote -v

gh repo view \
  --json nameWithOwner,visibility,url
```

Expected visibility:

```text
PRIVATE
```

## Daily Git workflow

Before starting work:

```bash
cd /opt/ai/mc-ai-bot
git status
git pull --ff-only
```

After making changes:

```bash
git status
git diff
git add src scripts configs tests README.md pyproject.toml
git diff --cached
git commit -m "Describe the completed change"
git push
```

Avoid blindly using `git add .` after generating checkpoints or external files. The `.gitignore` protects known paths, but explicitly staging the intended project directories is easier to review.

## Feature branches

For larger work, use a separate branch:

```bash
git switch -c feature/gui-model-v2
```

Commit normally:

```bash
git add src/minepilot/gui tests README.md
git commit -m "Add screen-aware GUI policy"
git push -u origin feature/gui-model-v2
```

After review and merge:

```bash
git switch main
git pull --ff-only
git branch -d feature/gui-model-v2
```

Suggested branch names:

```text
feature/gui-model-v2
feature/world-model-v2
feature/policy-router
fix/gui-drag-normalization
docs/update-readme
```

## Reviewing changes before pushing

```bash
git status
git diff
git diff --cached
git log --oneline -5
```

Check what will be pushed:

```bash
git log --oneline origin/main..HEAD
```

## Restoring a modified tracked file

Discard uncommitted changes to one file:

```bash
git restore path/to/file.py
```

Unstage a file while preserving its modifications:

```bash
git restore --staged path/to/file.py
```

## Do not commit secrets

Before pushing, check the tracked source tree:

```bash
grep -RInE \
  --exclude-dir=.git \
  --exclude-dir=minerl \
  --exclude-dir=checkpoints \
  --exclude-dir=runs \
  '(api[_-]?key|password|secret|private[_-]?key|access[_-]?token)' \
  src scripts configs . || true
```

Some source-code matches may be false positives. Review every unexpected result.

Never commit:

```text
GitHub tokens
API keys
private SSH keys
passwords
.env files
model checkpoints containing sensitive data
```

If a secret is committed, deleting it in a later commit is not sufficient. Rotate the secret and clean it from Git history.

## Checkpoint and dataset storage

Model checkpoints and datasets are intentionally not stored in the normal Git repository.

Current locations:

```text
/opt/ai/mc-ai-bot/checkpoints/
/opt/ai/datasets/minecraft_custom/
```

For sharing trained models later, use one of:

- GitHub Releases
- Git LFS
- object storage
- an internal artifact server

Do not remove the checkpoint rules from `.gitignore` without deliberately selecting a large-file storage strategy.

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

## 1. Create and push the private backend repository

- initialize Git in `/opt/ai/mc-ai-bot`
- create `MinePilot-Backend`
- verify that its visibility is `PRIVATE`
- push the initial backend commit

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

- world and GUI episode summaries
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

- `src/minepilot/gui/inference.py`
- `src/minepilot/gui/server.py`
- Java GUI executor
- slot-center lookup
- mouse-button press/release control
- drag-state execution
- close-screen execution

## 8. Implement the policy router

Add:

- `src/minepilot/runtime/protocol.py`
- `src/minepilot/runtime/router.py`

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

## Phase 2 — Backend structure

- [x] Full pre-migration backup
- [x] Python package under `src/minepilot`
- [x] World and GUI code separation
- [x] Legacy code archiving
- [x] Checkpoint directory reorganization
- [x] Shell launchers
- [x] `pyproject.toml`
- [x] Git-ready `.gitignore`
- [ ] Private GitHub repository created and pushed

## Phase 3 — World Behavior Cloning

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

## Phase 4 — GUI Behavior Cloning

- [x] GUI action normalizer
- [x] GUI PyTorch dataset
- [x] Slot classification
- [x] Drag-sequence representation
- [x] GUI model smoke test
- [x] Headless CPU training
- [x] Post-migration training validation
- [ ] Screen-type input embedding
- [ ] Slot masking
- [ ] Balanced demonstration dataset
- [ ] Episode-level validation
- [ ] GUI offline inference
- [ ] GUI live inference
- [ ] Java GUI action executor

## Phase 5 — Runtime integration

- [x] TCP image transport
- [x] World keyboard and camera controller
- [x] Debug HUD
- [ ] Extended response protocol
- [ ] World/GUI policy router
- [ ] GUI action execution
- [ ] Safe handling of unsupported screens
- [ ] Runtime confidence thresholds

## Phase 6 — Autonomy

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

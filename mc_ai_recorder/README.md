# MinePilot AI

> Open-source framework for recording Minecraft gameplay, training autonomous agents with Behavior Cloning, and running real-time inference through TCP.

---

# Features

- ✅ Fabric gameplay recorder
- ✅ Episode-based dataset generator
- ✅ Automatic dataset validation
- ✅ Behavior Cloning training with PyTorch
- ✅ ResNet18 vision model
- ✅ Frame stacking with four consecutive observations
- ✅ Single-image and stacked-frame inference
- ✅ Real-time TCP client/server communication
- ✅ Full keyboard control
- ✅ Camera control
- ✅ In-game debug HUD
- ✅ Legacy file-based live inference fallback
- ✅ Separate training and validation episodes

Planned:

- Dataset balancing
- Better visual backbones
- MineRL integration
- Reinforcement Learning
- Temporal models such as LSTM and Transformers
- Long-term memory
- Multi-agent support

---

# Architecture

```text
Minecraft on Windows
        │
        ▼
Fabric Recorder
        │
        ▼
Episode Dataset
        │
        ▼
Linux Training Server
        │
        ▼
PyTorch Behavior Cloning
        │
        ▼
TCP Inference Server
        │
        ▼
Minecraft AI Controller
```

For live frame stacking:

```text
Minecraft sends one JPEG
        │
        ▼
Python keeps the last four frames
        │
        ▼
[frame t-3, frame t-2, frame t-1, frame t]
        │
        ▼
12-channel ResNet18
        │
        ▼
Keyboard and camera actions
```

---

# Requirements

## Windows

- Minecraft Java Edition 26.1.2
- Prism Launcher
- Fabric Loader
- Fabric API
- Java 25
- Gradle wrapper included with the project

## Linux

- Ubuntu 24.04
- Python environment managed with Conda
- PyTorch
- torchvision
- Pillow
- tqdm
- NumPy

The current Conda environment is:

```bash
conda activate minerl-bot
```

---

# Project Structure

## Fabric mod

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

## AI training project

```text
/opt/ai/mc-ai-bot/
├── checkpoints/
│   ├── best_model.pt
│   └── last_model.pt
├── checkpoints_stack/
│   ├── best_model.pt
│   └── last_model.pt
├── tools/
│   ├── dataset_validator.py
│   └── inspect_training_dataset.py
└── training/
    ├── dataset.py
    ├── model.py
    ├── train_bc.py
    ├── inference.py
    ├── inference_server.py
    ├── dataset_stack.py
    ├── model_stack.py
    ├── train_bc_stack.py
    ├── inference_stack.py
    └── inference_server_stack.py
```

---

# Building the Fabric Mod

From Windows PowerShell:

```powershell
cd G:\MinePilot\MinePilot\mc_ai_recorder
.\gradlew.bat clean build
```

The compiled mod will be created in:

```text
build\libs\
```

Copy the generated JAR without `sources` in its name to the Prism Launcher instance `mods` directory.

Do not mark the entire project directory as a Sources Root in IntelliJ. Gradle automatically manages:

```text
src/client/java
```

and:

```text
src/main/resources
```

---

# Minecraft Controls

| Key | Action                          |
|-----|---------------------------------|
| F8  | Start or stop dataset recording |
| F9  | Legacy live-frame export        |
| F10 | Enable or disable AI control    |
| F12 | Enable or disable TCP inference |
| H   | Enable or disable HUD           |

Recommended live AI startup order:

```text
1. Start the Linux inference server
2. Press F12
3. Wait for TCP connection
4. Press F10
```

Pressing `F10` again releases AI-controlled keys.

---

# Recording a Dataset

Press:

```text
F8
```

to start recording.

Press:

```text
F8
```

again to stop recording.

Recordings are saved as separate episodes:

```text
episode_xxxxx/
├── frames/
├── actions.jsonl
└── metadata.json
```

Each entry contains:

- frame filename
- keyboard state
- camera movement
- player position
- health and food
- selected item
- dimension and biome
- movement state
- timestamp and tick data

The current frame resolution is:

```text
224 × 224 RGB
```

---

# Dataset Location

Copy recorded episodes to:

```text
/opt/ai/datasets/minecraft_custom
```

Example:

```text
/opt/ai/datasets/minecraft_custom/
├── episode_00001/
├── episode_00002/
├── episode_00003/
└── episode_00004/
```

---

# Dataset Validation

Activate the environment:

```bash
conda activate minerl-bot
```

Run:

```bash
python /opt/ai/mc-ai-bot/tools/dataset_validator.py \
  /opt/ai/datasets/minecraft_custom
```

The validator checks whether:

- episode directories are readable
- `actions.jsonl` exists
- referenced frames exist
- JSON entries can be parsed
- dataset structure is valid

---

# Classic Single-Frame Model

The original model processes one image at a time.

Input:

```text
[3, 224, 224]
```

The three input channels are:

```text
Red
Green
Blue
```

## Training

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python train_bc.py \
  --dataset /opt/ai/datasets/minecraft_custom \
  --output /opt/ai/mc-ai-bot/checkpoints \
  --epochs 5 \
  --batch-size 32 \
  --workers 4
```

Models are saved as:

```text
/opt/ai/mc-ai-bot/checkpoints/best_model.pt
/opt/ai/mc-ai-bot/checkpoints/last_model.pt
```

## Single-image inference

```bash
python inference.py \
  --checkpoint /opt/ai/mc-ai-bot/checkpoints/best_model.pt \
  --image /path/to/frame.png
```

## Live server

```bash
python inference_server.py
```

---

# Frame Stacking

The frame-stacking model receives the last four observations instead of only one screenshot.

Example history:

```text
frame t-3
frame t-2
frame t-1
frame t
```

Each frame has three RGB channels:

```text
[3, 224, 224]
```

Four frames are concatenated across the channel dimension:

```text
4 × 3 channels = 12 channels
```

Final model input:

```text
[12, 224, 224]
```

This gives the model short-term information about:

- player movement
- camera rotation
- approaching obstacles
- falling
- jumping
- moving targets
- changes between consecutive observations

The current configuration uses:

```text
stack_size = 4
frame_stride = 4
```

For the action associated with frame `100`, training may use:

```text
88, 92, 96, 100
```

The previous three frames provide context, while the final frame represents the current state.

---

# Testing the Frame-Stack Dataset

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python - <<'PY'
from dataset_stack import MinecraftFrameStackDataset

dataset = MinecraftFrameStackDataset(
    "/opt/ai/datasets/minecraft_custom",
    stack_size=4,
    frame_stride=4,
)

images, buttons, camera = dataset[100]

print("Samples:", len(dataset))
print("Images:", images.shape)
print("Buttons:", buttons.shape)
print("Camera:", camera.shape)
PY
```

Expected input shape:

```text
torch.Size([12, 224, 224])
```

Expected action shapes:

```text
Buttons: torch.Size([9])
Camera: torch.Size([2])
```

---

# Training the Frame-Stack Model

Frame stacking uses separate files so the working single-frame model remains untouched.

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

The lower default batch size is recommended because the model receives four times more image channels.

Models are saved as:

```text
/opt/ai/mc-ai-bot/checkpoints_stack/best_model.pt
/opt/ai/mc-ai-bot/checkpoints_stack/last_model.pt
```

The stack training script stores additional checkpoint metadata:

```text
stack_size
frame_stride
button_outputs
action_keys
camera_weight
epoch
validation loss
```

Training and validation are split by episode when multiple episodes are available. This prevents nearly identical neighboring frames from appearing in both training and validation sets.

---

# Frame-Stack Offline Inference

## One image

When only one image is provided, it is repeated four times:

```bash
python inference_stack.py \
  --checkpoint /opt/ai/mc-ai-bot/checkpoints_stack/best_model.pt \
  --images /path/to/frame.png
```

The model receives:

```text
[frame, frame, frame, frame]
```

## Four images

Provide images from oldest to newest:

```bash
python inference_stack.py \
  --checkpoint /opt/ai/mc-ai-bot/checkpoints_stack/best_model.pt \
  --images \
  frame_0088.png \
  frame_0092.png \
  frame_0096.png \
  frame_0100.png
```

The script prints:

- input tensor shape
- frames used
- probability for every button
- predicted yaw delta
- predicted pitch delta

---

# Live Frame-Stack TCP Inference

Stop the original inference server before starting the stacked version because both use TCP port `5005`.

Start the server:

```bash
cd /opt/ai/mc-ai-bot/training
conda activate minerl-bot

python inference_server_stack.py
```

Expected startup output:

```text
Starting frame-stack inference server on 0.0.0.0:5005
Waiting for Minecraft client...
```

Minecraft still sends one JPEG per request.

The Python server maintains a queue containing the last four received frames:

```text
Request 1: [A, A, A, A]
Request 2: [A, A, A, B]
Request 3: [A, A, B, C]
Request 4: [A, B, C, D]
Request 5: [B, C, D, E]
```

No Java TCP protocol changes are required.

In Minecraft:

```text
F12 — enable TCP inference
F10 — enable AI control
```

---

# TCP Protocol

Default server:

```text
Linux host: 192.168.0.11
TCP port: 5005
```

Current Windows client:

```text
192.168.0.50
```

Request format:

```text
4-byte big-endian image length
JPEG image bytes
```

Response format:

```text
4-byte big-endian JSON length
JSON response bytes
```

Example response:

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
  "probs": {
    "forward": 0.9731,
    "back": 0.0032
  },
  "camera": {
    "yawDelta": -0.42,
    "pitchDelta": 0.08
  }
}
```

---

# Debug HUD

The Fabric mod displays an in-game debug HUD.

The HUD shows:

```text
TCP state
AI control state
Connection state
Round-trip latency
JPEG size
Current movement actions
Jump, sneak and sprint state
Attack and use state
Yaw delta
Pitch delta
```

Example:

```text
OpenCraft AI
TCP: ON
Control: ON
Connected: true
RTT: 31 ms
JPEG: 8421 bytes
F:true B:false L:false R:false
J:false Sneak:false Sprint:true
Attack:false Use:false
Yaw: -0.42 Pitch: 0.08
```

---

# Current Model

Backbone:

```text
ResNet18
```

Single-frame input:

```text
[3, 224, 224]
```

Frame-stack input:

```text
[12, 224, 224]
```

Outputs:

```text
Forward
Back
Left
Right
Jump
Sneak
Sprint
Attack
Use

Yaw Delta
Pitch Delta
```

Button predictions use independent sigmoid probabilities.

Default action threshold:

```text
0.5
```

Camera movement is predicted using two continuous values:

```text
yawDelta
pitchDelta
```

---

# Training Tips

Record demonstrations containing:

- normal walking
- sprinting
- strafing
- moving backwards
- jumping over obstacles
- looking around smoothly
- approaching trees and blocks
- mining
- using items
- combat
- exploring terrain
- swimming
- recovering after hitting obstacles

Avoid excessive:

- AFK time
- standing still
- random camera spinning
- menu usage unrelated to the task
- repeated identical behavior
- recordings with very low FPS

The model only learns actions that exist in the dataset. Frame stacking gives temporal context, but it does not solve missing or heavily underrepresented actions.

For example, when very few samples contain:

```text
jump = true
```

the model may still rarely jump.

---

# Switching Between Models

## Original single-frame model

```bash
python inference_server.py
```

Checkpoint:

```text
/opt/ai/mc-ai-bot/checkpoints/best_model.pt
```

## Frame-stack model

```bash
python inference_server_stack.py
```

Checkpoint:

```text
/opt/ai/mc-ai-bot/checkpoints_stack/best_model.pt
```

Only one server can use port `5005` at a time.

---

# Roadmap

## Phase 1 — Data collection

- [x] Fabric recorder
- [x] Frame capture
- [x] Action recording
- [x] Episode metadata
- [x] Dataset validation

## Phase 2 — Behavior Cloning

- [x] PyTorch dataset
- [x] ResNet18 model
- [x] Keyboard prediction
- [x] Camera prediction
- [x] Checkpoint saving
- [x] Offline inference

## Phase 3 — Real-time agent

- [x] TCP client
- [x] TCP server
- [x] Real-time keyboard control
- [x] Real-time camera control
- [x] Debug HUD
- [x] Frame stacking

## Phase 4 — Model improvements

- [ ] Dataset balancing
- [ ] Per-action loss weights
- [ ] Data augmentation
- [ ] Better train/validation metrics
- [ ] Confusion statistics for each action
- [ ] Better backbone
- [ ] Temporal LSTM or Transformer
- [ ] Larger demonstration dataset

## Phase 5 — Reinforcement Learning

- [ ] MineRL integration
- [ ] Reward system
- [ ] Behavior Cloning initialization
- [ ] RL fine-tuning
- [ ] Curriculum learning

## Phase 6 — Autonomous Minecraft Agent

- [ ] Long-term memory
- [ ] Goal planning
- [ ] Inventory awareness
- [ ] Crafting planner
- [ ] Navigation and mapping
- [ ] Multi-agent support

---

# License

MIT

---

# Author

Juliusz Wójcik

Pixeloza
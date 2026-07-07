# OpenCraft AI

> Open-source framework for training autonomous Minecraft agents using Behavior Cloning and Reinforcement Learning.

---

# Features

✅ Fabric Recorder

✅ Dataset Generator

✅ Automatic Episode Validation

✅ Behavior Cloning Training (PyTorch)

✅ Live Inference

✅ TCP Client/Server

✅ Real-time AI Control

Planned:

- MineRL integration
- Reinforcement Learning
- Foundation Models
- Multi-Agent Support

---

# Architecture

```
Minecraft (Windows)

↓

Fabric Recorder

↓

Dataset

↓

Linux Training Server

↓

PyTorch

↓

TCP Inference

↓

Minecraft
```

---

# Requirements

## Windows

- Prism Launcher
- Fabric
- Java 21
- Minecraft 1.21.6
- Fabric API

## Linux

Ubuntu 24.04

Python 3.11

PyTorch

torchvision

Pillow

numpy

---

# Project Structure

```
mc-ai-recorder/
```

Fabric mod.

```
mc-ai-bot/
```

Training code.

```
datasets/
```

Recorded demonstrations.

```
checkpoints/
```

Saved models.

---

# Recording Dataset

Press:

```
F8
```

Start recording.

Press:

```
F8
```

Stop recording.

Dataset will be saved as

```
episode_xxxxx
```

containing

```
frames/
actions.jsonl
metadata.json
```

---

# Dataset Validation

Copy episodes to

```
/opt/ai/datasets/minecraft_custom
```

Run

```bash
python validate_dataset.py /opt/ai/datasets/minecraft_custom
```

---

# Training

Activate environment

```bash
conda activate minerl-bot
```

Run

```bash
python train_bc.py \
    --dataset /opt/ai/datasets/minecraft_custom \
    --epochs 20 \
    --batch-size 32
```

Model is saved as

```
checkpoints/best_model.pt
```

---

# Single Image Inference

```bash
python inference.py \
 --checkpoint checkpoints/best_model.pt \
 --image frame.png
```

---

# Live TCP Inference

Start Linux server

```bash
python inference_server.py
```

Should print

```
Waiting for Minecraft client...
```

---

# Minecraft Controls

| Key | Action |
|------|--------|
| F8 | Start/Stop Recording |
| F9 | Live Frame Export (legacy) |
| F10 | AI Control |
| F12 | TCP Inference |

---

# Live AI

Linux

```bash
python inference_server.py
```

Minecraft

```
F12
```

Connect to AI.

```
F10
```

AI takes control.

---

# Network

Default

```
Windows
192.168.0.50
```

```
Linux
192.168.0.11
```

TCP

```
5005
```

---

# Current Model

Backbone

```
ResNet18
```

Input

```
224x224 RGB
```

Outputs

```
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

---

# Training Tips

Recommended demonstrations:

- Walking
- Sprinting
- Looking Around
- Mining
- Building
- Inventory
- Combat
- Crafting
- Exploring
- Swimming

Avoid:

- AFK
- Standing Still
- Random Camera Spins

---

# Roadmap

## Phase 1

- Recorder

- BC

- TCP

✔ DONE

---

## Phase 2

- Better Backbone

- More Demonstrations

- Dataset Balancing

---

## Phase 3

MineRL

---

## Phase 4

Reinforcement Learning

---

## Phase 5

Autonomous Minecraft Agent

---

# License

MIT

---

# Author

Juliusz Wójcik

OpenCraft AI
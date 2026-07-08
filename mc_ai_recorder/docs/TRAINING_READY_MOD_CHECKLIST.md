# MinePilot Mod 1.0.0 — Training-Ready Freeze

Final invariants:

- Minecraft Java version: 26.1.2
- Fabric Loader: 0.19.3
- Fabric API: 0.154.0+26.1.2
- Java: 25
- Protocol: 2
- Schema: 2
- Runtime endpoint comes from `config/minepilot-runtime.json`
- Required backend capabilities are enforced
- F10 is required for execution
- F12 controls TCP
- WORLD actions are blocked while GUI is open
- GUI actions require exact context and current fingerprint
- Creative GUI execution is blocked
- Stale actions release controls and enter safe idle
- Final JAR SHA-256 is written to the release manifest

Recommended tag:

```text
mod-training-ready-v1.0.0
```

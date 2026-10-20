# Mochi — Architecture

Mochi is a **hybrid AI coding agent**: heavy GPU-bound LLM inference, GitHub
repo state and long-running task delegation run in a **cloud backend**, while a
**native Android app** embeds a local LLM (`llama.cpp`/MLC-LLM via JNI) and a
PRoot-bootstrapped Alpine Linux terminal so it can keep working offline.

```
┌────────────────────────────── Android device ──────────────────────────────┐
│                                                                            │
│  Compose UI (black/blue)                                                   │
│     │ Kotlin Flows                                                         │
│     ▼                                                                      │
│  MochiViewModel ──────────────► AgentStateFlow ──────► MochiAvatarView     │
│     │  ▲                                                            │      │
│     │  │ state published / observed                                   │   (Lottie/Rive)
│     ▼  │                                                              │      │
│  LocalLlmService (foreground)                                    CloudWebSocket │
│     │ JNI (llama.cpp)                                                  ▸  ws://cloud
│     ▼                                                                      │
│  TerminalService (background)                                              │
│     └─► PRoot ─► Alpine rootfs (clang, python3, git)                       │
│            └─► git credentials from TokenStore ──► clone/commit/push       │
└───────────────────────────────┬───────────────▲────────────────────────────┘
                                │               │
                         HTTP REST (JWT)   WebSocket (JWT ?token=)
                                ▼               │
┌────────────────────────────── Cloud backend ───────────────────────────────┐
│  FastAPI                                                                    │
│   ├─ /api/auth           device registration + refresh (JWT)                │
│   ├─ /api/llm/chat       OpenAI/Anthropic provider proxy                    │
│   ├─ /api/github         GitHub REST proxy (issues/PRs/repos)               │
│   ├─ /api/projects       repository state + checkout paths                  │
│   ├─ /api/tasks          long-running task executor (asyncio)               │
│   ├─ /api/state          REST fallback for agent-state sync                 │
│   └─ /ws                 authenticated WebSocket + ConnectionHub            │
│  PostgreSQL / SQLite (SQLAlchemy) ── Devices, Sessions, Projects, Tasks     │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Agent states and the avatar

The single source of truth is `AgentState` (`IDLE`, `THINKING`, `ACTION`,
`CODING`, `OFFLINE`, `ERROR`). `MochiViewModel` observes `AgentStateFlow` and
renders:

| State      | UI                         | Files                                        |
| ---------- | -------------------------- | -------------------------------------------- |
| `THINKING` | pulsing head + ring        | `res/raw/mochi_thinking.json` (Lottie)       |
| `ACTION`   | head + action shimmer      | `res/raw/mochi_action.json` (Lottie)         |
| `CODING`   | full-body walking keyboard | `res/raw/mochi_coding.riv` (Rive)            |
| `IDLE`     | floating/blinking head     | `res/raw/mochi_idle.json` (Lottie)           |

When a raw asset is missing the view falls back to a Compose-drawn animated
head, so the app never renders a blank avatar.

## WebSocket protocol

Covered in `WS_PROTOCOL.md`. The client (`CloudWebSocketClient`) authenticates
with `?token=<JWT>`, receives `hello`, `heartbeat`, `agent.state`, `state`,
`event` and `error` frames, and publishes `agent.state`, `ping` and
`state.get`.

## Security model

* Cloud: devices register once with a SHA-256 `public_key_digest`; the backend
  issues a short-lived JWT (stored in the app via `EncryptedSharedPreferences`).
* GitHub: the app reads the user PAT from `EncryptedSharedPreferences`; if none
  is configured it falls back to `SecretConfig.DEFAULT_GITHUB_TOKEN` (MochiiAI
  service account), which is injected at build time from the Gradle property
  `mochiDefaultGitHubToken` (`android/local.properties`) and is empty in the
  open repository. The PRoot script writes the token to `.netrc` /
  `.git-credentials` so `git clone/commit/push` work inside the terminal.

> Shipping a fallback credential in the APK is a dev convenience only — replace
> `mochiDefaultGitHubToken` before distributing. Never commit a live PAT:
> GitHub secret scanning blocks pushes that contain one.

## Repository state management

`/api/projects` mirrors GitHub repositories into a `checkout_path` (via the
cloud executor) and records the branch Mochi is working on. The Android app can
create and list projects, then delegate tasks (`/api/tasks`) that the cloud
executor turns into ordered git commands + LLM completion.
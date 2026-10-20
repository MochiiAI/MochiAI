<div align="center">

<img src="docs/images/mochi-icon.png" alt="Mochi app icon" width="128" height="128" />

# Mochi — Hybrid AI Coding Agent

_Your on-device coder that pairs a local LLM terminal with a cloud brain._

[![Backend tests](https://img.shields.io/github/actions/workflow/status/MochiiAI/MochiAI/backend.yml?branch=main&label=backend%20CI&logo=github-actions&logoColor=white&style=for-the-badge)](https://github.com/MochiiAI/MochiAI/actions/workflows/backend.yml)
[![Android APK build](https://img.shields.io/github/actions/workflow/status/MochiiAI/MochiAI/build.yml?branch=main&label=android%20APK&logo=android&logoColor=white&style=for-the-badge)](https://github.com/MochiiAI/MochiAI/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.150-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Platform](https://img.shields.io/badge/Platform-Android%20%2B%20Cloud-2A6CFB?style=for-the-badge&logo=android&logoColor=white)](#)

</div>

Mochi is a hybrid AI coding agent built from a **native Android app** that embeds a
local LLM and a **PRoot-based Alpine Linux terminal**, plus a **cloud backend**
(FastAPI + WebSockets) that handles heavy LLM inference, GitHub repository state
and long-running task delegation.

Mochi can think, act and code — on-device and offline via `llama.cpp`/`MLC-LLM`,
or remotely by delegating work to the cloud backend.

## Repository Layout

```
MochiAI/
├── backend/                        # FastAPI cloud backend
│   ├── app/
│   │   ├── main.py                 # FastAPI app wiring + routers
│   │   ├── config.py               # settings (JWT, DB, LLM providers)
│   │   ├── database.py             # SQLAlchemy engine/session
│   │   ├── models.py               # Device, Session, Project, Task
│   │   ├── schemas.py              # Pydantic request/response models
│   │   ├── security.py             # JWT issuance/verification, digests
│   │   ├── routers/                # /api/auth, /api/state, /api/llm,
│   │   │                           #   /api/github, /api/projects, /api/tasks
│   │   ├── services/               # github.py, llm.py, executor.py
│   │   └── ws/                     # WebSocket endpoint + ConnectionHub
│   ├── alembic/                    # migrations (initial schema applied)
│   ├── tests/                      # 29 passing tests
│   └── pyproject.toml
├── android/                        # Native Android app (Kotlin, Compose)
│   ├── gradle/libs.versions.toml   # version catalog
│   ├── settings.gradle.kts / build.gradle.kts / gradle.properties
│   ├── gradlew / gradlew.bat / gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/mochi/agent/
│           │   ├── MainActivity.kt / MochiApplication.kt
│           │   ├── core/
│           │   │   ├── SecretConfig.kt                # DEFAULT_GITHUB_TOKEN
│           │   │   ├── llm/  (MochiNativeBridge, LocalLlmService, AgentState)
│           │   │   └── security/TokenStore.kt         # EncryptedSharedPreferences
│           │   ├── data/
│           │   │   ├── cloud/  (CloudApi, CloudWebSocketClient, models)
│           │   │   └── github/ (GitHubApi, GitHubService, AuthInterceptor)
│           │   ├── proroot/    (AndroidPrRootManager, TerminalSession,
│           │   │                TerminalService, TerminalBus)
│           │   ├── state/AgentStateFlow.kt
│           │   ├── viewmodel/MochiViewModel.kt
│           │   └── ui/         (MainScreen, screens/, components/, theme/)
│           ├── cpp/             # JNI C++ bridge (CMakeLists, llama_jni.h,
│           │                    #   mochi_llm_bridge.cpp)
│           ├── assets/preroot/  # proot-init.sh, bootstrap-alpine.sh
│           └── res/
│               ├── raw/        # placeholder for Lottie/Rive avatar files
│               ├── drawable/   # vector launcher icon (blue/black/white)
│               ├── mipmap-anydpi-v26/ (adaptive icon)
│               └── values/     # strings, colors, themes
├── docs/             # Architecture, protocol and setup guides
│   ├── animation-assets.md  # Lottie/Rive avatar asset spec + placeholder
│   └── images/       # mochi-icon.png (README header)
└── .github/          # GitHub Actions CI
    └── workflows/    # backend.yml (pytest+ruff), build.yml (Android APK)
```

| Component | Location |
|---|---|
| Cloud backend | `backend/` |
| Android app / Gradle | `android/` |
| JNI C++ bridge (local LLM) | `android/app/src/main/cpp/` |
| PRoot Alpine init scripts | `android/app/src/main/assets/preroot/` |
| Lottie / Rive Mochi avatar assets | `android/app/src/main/res/raw/` |
| Android manifest / Gradle files | `android/app/`, `android/gradle/` |

## Getting Started

**Backend**

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head           # migrate the SQLite/Postgres schema
uvicorn app.main:app --reload  # http://127.0.0.1:8000  (ws://127.0.0.1:8000/ws)
```

**Android**

```bash
cd android
cp local.properties.example local.properties   # optional: MOCHI_DEFAULT_GITHUB_TOKEN
./gradlew :app:assembleDebug
```

The app falls back to the `MochiiAI` GitHub token when the user has not added
their own Personal Access Token (see `SecretConfig.kt`, `TokenStore`
/`EncryptedSharedPreferences`). The fallback token is *injected at build time*
from the Gradle property `mochiDefaultGitHubToken` (`android/local.properties`)
so the repository never contains a live credential — GitHub push protection
blocks commits that embed PATs.

## Continuous Integration

GitHub Actions runs two workflows on every push and pull request:

| Workflow | File | What it does | Artifacts |
|---|---|---|---|
| **backend** | `.github/workflows/backend.yml` | Installs the backend, runs `ruff` + `pytest` (29 tests, SQLite in-memory) | — |
| **build** | `.github/workflows/build.yml` | Builds the Android app (`assembleDebug` + `assembleRelease`) with JDK 17 | `mochi-app-debug.apk`, `mochi-app-release-unsigned.apk` |

Grab an APK from the **Actions** tab → *Android APK build* → *Artifacts* at the
bottom of the run. The fallback `MOCHI_DEFAULT_GITHUB_TOKEN` may be set as a
repository *Actions secret* to bake the MochiiAI service-account token into
release builds (optional).

## Highlights

- **Agent state → UI animation mapping** (`THINKING`, `ACTION`, `CODING`, `IDLE`)
  drives a Lottie/Rive Mochi avatar through `MochiViewModel`.
- **PRoot** bootstraps a lightweight Alpine Linux rootfs (clang, python3, git) so
  Mochi can run terminal commands on-device, with Git credentials injected
  automatically.
- **JNI bridge** (`mochi_llm_bridge.cpp`) exposes a C API surface for
  `llama.cpp`/`MLC-LLM` inference inside a background Android Service.
- **WebSocket sync** keeps app and cloud agents in lock-step
  (`AgentSyncMessage` protocol — see `docs/WS_PROTOCOL.md`).
- **GitHub integration** fuses the REST API (issues, PRs) with the terminal
  (clone/commit/push) using one shared PAT.

## License

Proprietary internal project.

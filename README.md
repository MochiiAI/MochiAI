# Mochi — Hybrid AI Coding Agent

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
└── .github/          # GitHub Actions CI
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

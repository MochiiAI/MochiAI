# Mochi Avatar animations

`android/app/src/main/res/raw/` is the canonical place for the Lottie
(`*.json`) and Rive (`*.riv`) animation files that drive the Mochi avatar.

The app resolves animations by backend agent state:

| State      | Animation file (expected)     | Visual                                          |
| ---------- | ----------------------------- | ----------------------------------------------- |
| `THINKING` | `mochi_thinking.json`         | Mochi head with animated thinking dots / face   |
| `ACTION`   | `mochi_action.json`           | Mochi head + action label (e.g. "running shell")|
| `CODING`   | `mochi_coding.riv`            | Full-body Mochi walking across a keyboard       |
| `IDLE`     | `mochi_idle.json`             | Blinking / floating & spinning head             |

When a file is missing, [MochiAvatarView] falls back to a Compose-drawn
animated head so the UI never breaks.

> These animation assets are authored in Adobe After Effects (Lottie) or the
> Rive editor and exported here. They are not committed to git
> (see `.gitignore`, `res/raw/*.json` + `res/raw/*.riv`), so drop them in as
> part of the release pipeline or the Mochi designer portal.

Placeholder naming convention:
- `mochi_thinking.json` — must animate a pulsing “Mochi is thinking…“ face
- `mochi_action.json` — same head, larger `action_name` shimmer
- `mochi_idle.json` — slow float + occasional blink
- `mochi_coding.riv` — state machine with `idle` / `walk` states driven by
  input `speed` and `typing` booleans

## Why the placeholder is a text file

Android resource names must be lowercase `a-z`, `0-9` or `_`, and every file
under `res/` is compiled into the APK. A `README.md` in `res/raw/` breaks the
build (`'R' is not a valid file-based resource name character`). The directory
therefore ships a `placeholder.txt`; the Lottie/Rive files themselves are
ignored by git and dropped in at packaging time.
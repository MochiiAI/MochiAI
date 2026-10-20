# Mochi Avatar animations

This directory is the canonical place for the Lottie (`*.json`) and Rive
(`*.riv`) animation files that drive the Mochi avatar.

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
> Rive editor and exported here. They are excluded from version control
> (see `.gitignore`, `res/raw/*.json` + `res/raw/*.riv`), so drop them in as
> part of the release pipeline or the Mochi designer portal.

Placeholder naming convention:
- `mochi_thinking.json` — must animate a pulsing “Mochi is thinking…“ face
- `mochi_action.json` — same head, larger `action_name` shimmer
- `mochi_idle.json` — slow float + occasional blink
- `mochi_coding.riv` — state machine with `idle` / `walk` states driven by
  input `speed` and `typing` booleans
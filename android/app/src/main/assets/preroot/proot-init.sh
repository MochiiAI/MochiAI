#!/system/bin/sh
# ---------------------------------------------------------------------------
# proot-init.sh — bootstrap an Alpine Linux rootfs for the Mochi terminal.
#
# Extracted from bundled assets (see AndroidPrRootManager) so the rootfs can be
# refreshed independently of the APK. When no rootfs archive is shipped this
# script still provisions git credentials and a minimal /root home, so Mochi
# can drive git+ssh through the terminal immediately.
#
# Runs INSIDE the app-private storage directory (prRootDir).
# ---------------------------------------------------------------------------
set -e

PRROOT_DIR="${1:-$HOME/.mochi/prroot}"
export HOME="$PRROOT_DIR/home"
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

log() { echo "[mochi-proot] $*"; }

mkdir -p "$PRROOT_DIR/tmp" "$PRROOT_DIR/home" "$PRROOT_DIR/etc"

# ----.gitconfig / .netrc with the active GitHub token (0 = this device) -----
TOKEN=""
if [ -n "$MOCHI_GITHUB_TOKEN" ]; then
  TOKEN="$MOCHI_GITHUB_TOKEN"
elif [ -f "$PRROOT_DIR/etc/mochi-github-token" ]; then
  TOKEN="$(cat "$PRROOT_DIR/etc/mochi-github-token")"
fi

if [ -n "$TOKEN" ]; then
  # token-managed credential helper is preferred (no plaintext on disk after run)
  git config --global credential.helper store 2>/dev/null || true
  printf 'https://MochiiAI:%s@github.com\n' "$TOKEN" \
    > "$PRROOT_DIR/home/.git-credentials" 2>/dev/null || true
  # fallback .netrc so clone/push work even when credential.helper is missing
  printf 'machine github.com\nlogin MochiiAI\npassword %s\n' "$TOKEN" \
    > "$PRROOT_DIR/home/.netrc"
  chmod 600 "$PRROOT_DIR/home/.netrc" "$PRROOT_DIR/home/.git-credentials" 2>/dev/null || true
  git config --global user.name "MochiiAI" 2>/dev/null || true
  git config --global user.email "MochiAII@protonmail.com" 2>/dev/null || true
  git config --global init.defaultBranch main 2>/dev/null || true
else
  log "no GitHub token provided; skipping credential configuration"
fi

log "PRoot environment ready at $PRROOT_DIR"

# Drop into an interactive shell if attached, else stay alive.
if [ ! -t 0 ]; then
  exec /bin/sh
fi
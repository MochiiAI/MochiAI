#!/system/bin/sh
# ---------------------------------------------------------------------------
# bootstrap-alpine.sh — fetch a minimal Alpine Linux rootfs tarball.
#
# Designed to run inside the app's PRoot directory (no root required) and
# produces `rootfs/minirootfs-alpine.tar.gz`, which proot-init.sh extracts.
#
# If the build bundles a rootfs tarball under assets/preroot/ that is used
# instead; this script exists for updating/flashing a fresh Alpine on-device.
#
# Usage: bootstrap-alpine.sh <arch> <prroot-dir>
#   arch: aarch64 (arm64-v8a) or x86_64
# ---------------------------------------------------------------------------
set -e

ARCH="${1:-aarch64}"
PRROOT_DIR="${2:-$HOME/.mochi/prroot}"
MIRROR="https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/${ARCH}"

# Latest minirootfs filename is discovered via the sha256sums index.
SUMS="$(curl -fsSL "$MIRROR/sha256sums" || exit 1)"
TARBALL="$(echo "$SUMS" | awk '/minirootfs-/ { print $2 }' | head -n1)"

if [ -z "$TARBALL" ]; then
  echo "[mochi] could not discover Alpine minirootfs on $MIRROR" >&2
  exit 1
fi

echo "[mochi] downloading $MIRROR/$TARBALL"
mkdir -p "$PRROOT_DIR"
curl -fSL "$MIRROR/$TARBALL" -o "$PRROOT_DIR/$TARBALL"

# Verify checksum (first column is the sha256 for that file).
for hash in $(echo "$SUMS" | grep -F "$TARBALL"); do
  case "$hash" in
    [0-9a-f]*)
      echo "$hash  $PRROOT_DIR/$TARBALL" > "$PRROOT_DIR/checksum.txt"
      break
      ;;
  esac
done
[ -s "$PRROOT_DIR/checksum.txt" ] && (cd "$PRROOT_DIR" && sha256sum -c checksum.txt)

echo "[mochi] bootstrapped $TARBALL; run proot-init.sh to install tools"
echo "  packages: apk add build-base python3 git clang cmake"
#!/usr/bin/env bash
# sync.sh — mirror the project between the WSL working copy (where Claude edits)
# and the Windows-side Android Studio copy (where Gradle builds + the device runs).
#
# Usage:
#   ./scripts/sync.sh push   # WSL → Windows  — run after Claude / WSL-side edits.
#   ./scripts/sync.sh pull   # Windows → WSL  — run after Android Studio edits.
#   ./scripts/sync.sh diff   # dry-run both directions; show what would change.
#
# Both directions use rsync --delete, so the destination becomes a faithful
# mirror of the source (excluding generated/IDE state — see EXCLUDES).
# Always run `pull` before resuming WSL-side editing if you've changed files
# in Android Studio, otherwise `push` will overwrite them.

set -euo pipefail

WSL_DIR="/home/anca/facultate/google_hackathon"
WIN_DIR="/mnt/c/Users/ancas/StudioProjects/google_hackathon"

EXCLUDES=(
    --exclude='/.git/'
    --exclude='/.idea/'
    --exclude='/.gradle/'
    --exclude='/.kotlin/'
    --exclude='/.dist/'
    --exclude='**/build/'
    --exclude='**/.cxx/'
    --exclude='**/.externalNativeBuild/'
    --exclude='/captures/'
    --exclude='*.iml'
    --exclude='/local.properties'
)

cmd="${1:-}"

case "$cmd" in
    push)
        echo "Syncing WSL → Windows…"
        rsync -av --delete "${EXCLUDES[@]}" "$WSL_DIR/" "$WIN_DIR/"
        echo
        echo "Done. In Android Studio: File → Sync Project with Gradle Files."
        ;;
    pull)
        echo "Syncing Windows → WSL…"
        rsync -av --delete "${EXCLUDES[@]}" "$WIN_DIR/" "$WSL_DIR/"
        echo
        echo "Done."
        ;;
    diff)
        echo "=== Would push (WSL → Windows) ==="
        rsync -avn --delete "${EXCLUDES[@]}" "$WSL_DIR/" "$WIN_DIR/"
        echo
        echo "=== Would pull (Windows → WSL) ==="
        rsync -avn --delete "${EXCLUDES[@]}" "$WIN_DIR/" "$WSL_DIR/"
        ;;
    *)
        echo "Usage: $0 {push|pull|diff}"
        exit 1
        ;;
esac

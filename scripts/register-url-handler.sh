#!/bin/sh
# Registers the contextswitcher:// URL protocol for the current user (https://github.com/contextswitcher/contextswitcher-private/issues/47, MADR 0016).
# Usage: register-url-handler.sh [path/to/ContextSwitcher/bin/ContextSwitcher]
# Default: ContextSwitcher/bin/ContextSwitcher next to this script (the packageApp zip layout);
#   in a source checkout, falls back to the locally built app image (./gradlew :app:jpackageImage,
#   or ./gradlew :app:registerUrlHandler to build and register in one step).
# Re-run after moving the app image - the .desktop file stores an absolute path.
set -e
EXE="${1:-$(dirname "$0")/ContextSwitcher/bin/ContextSwitcher}"
if [ -z "$1" ] && [ ! -x "$EXE" ]; then
  EXE="$(dirname "$0")/../app/build/jpackage/image/ContextSwitcher/bin/ContextSwitcher"
fi
if [ ! -x "$EXE" ]; then
  echo "ContextSwitcher launcher not found: $EXE" >&2
  echo "Build it first (./gradlew :app:jpackageImage) or pass its path as argument." >&2
  exit 1
fi
EXE="$(cd "$(dirname "$EXE")" && pwd)/$(basename "$EXE")"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$APPS"
cat > "$APPS/contextswitcher-url.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=ContextSwitcher URL Handler
Exec=$EXE %u
MimeType=x-scheme-handler/contextswitcher;
NoDisplay=true
EOF
update-desktop-database "$APPS" 2>/dev/null || true
xdg-mime default contextswitcher-url.desktop x-scheme-handler/contextswitcher
echo "contextswitcher:// links now open $EXE"

#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────
# Fabiodalez Music — Windows Git Bash Android overlay installer
# Copies Android-specific files into a Monochrome clone.
#
# Run from Git Bash, not Command Prompt:
#   bash install-windows.sh ../monochrome
# ─────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${1:-}" ]; then
    echo "Usage: bash install-windows.sh /path/to/monochrome"
    echo ""
    echo "Example from Git Bash:"
    echo "  cd /c/Users/flore/Documents/Projects/Monochrome-Android-APK"
    echo "  bash install-windows.sh ../monochrome"
    exit 1
fi

TARGET="$(cd "$1" && pwd)"

if [ ! -f "$TARGET/index.html" ] || [ ! -f "$TARGET/package.json" ]; then
    echo "Error: $TARGET doesn't look like a Monochrome project."
    echo "Make sure you cloned https://github.com/monochrome-music/monochrome beside this repo."
    exit 1
fi

echo "Installing Fabiodalez Music Android overlay into: $TARGET"

# The Windows build script expects an upstream remote.
if ! git -C "$TARGET" remote get-url upstream >/dev/null 2>&1; then
    echo "Adding upstream remote to Monochrome clone..."
    git -C "$TARGET" remote add upstream https://github.com/monochrome-music/monochrome.git
fi

# Copy build scripts and Capacitor config.
cp "$SCRIPT_DIR/build-android-windows.sh" "$TARGET/"
cp "$SCRIPT_DIR/capacitor.config.ts" "$TARGET/"

# Copy wrapper JS files.
mkdir -p "$TARGET/android"
cp "$SCRIPT_DIR/android/android-service.js" "$TARGET/android/"
cp "$SCRIPT_DIR/android/fm-logger.js" "$TARGET/android/"

# Copy Java sources.
JAVA_DEST="$TARGET/android/app/src/main/java/com/monochrome/app"
mkdir -p "$JAVA_DEST"
cp "$SCRIPT_DIR/android/app/src/main/java/com/monochrome/app/"*.java "$JAVA_DEST/"

# Copy AndroidManifest.
mkdir -p "$TARGET/android/app/src/main"
cp "$SCRIPT_DIR/android/app/src/main/AndroidManifest.xml" "$TARGET/android/app/src/main/"

# Copy resources.
RES_DEST="$TARGET/android/app/src/main/res"
for dir in values values-v31 drawable; do
    mkdir -p "$RES_DEST/$dir"
    cp "$SCRIPT_DIR/android/app/src/main/res/$dir/"* "$RES_DEST/$dir/" 2>/dev/null || true
done

# Copy icons.
for dir in mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi mipmap-xxxhdpi; do
    mkdir -p "$RES_DEST/$dir"
    cp "$SCRIPT_DIR/android/app/src/main/res/$dir/"* "$RES_DEST/$dir/" 2>/dev/null || true
done

# Copy Android app Gradle customizations.
cp "$SCRIPT_DIR/android/app/build.gradle" "$TARGET/android/app/"

echo ""
echo "Done. Now run from Git Bash:"
echo "  cd $TARGET"
echo "  bash build-android-windows.sh"

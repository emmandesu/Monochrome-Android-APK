#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────
# Fabiodalez Music — Android Build Script for Windows Git Bash
#
# Run from the Monochrome web app clone after installing the wrapper:
#   bash build-android-windows.sh
#
# Requirements:
#   - Git for Windows / Git Bash
#   - Node.js + npm installed on Windows
#   - Android Studio or Android command-line tools installed on Windows
#   - JDK 21 installed on Windows
#
# Optional environment variables:
#   JAVA_HOME
#   ANDROID_HOME
#   ANDROID_SDK_ROOT
# ─────────────────────────────────────────────────────────

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_OUTPUT="$PROJECT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
APK_COPY="$PROJECT_DIR/Monochrome-debug.apk"

# Convert common Windows paths into Git Bash paths when needed.
win_to_unix_path() {
    local p="${1:-}"
    if [ -z "$p" ]; then
        echo ""
        return
    fi
    cygpath -u "$p" 2>/dev/null || echo "$p"
}

# Auto-detect Android SDK from common Windows locations.
detect_android_home() {
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$(win_to_unix_path "$ANDROID_HOME")" ]; then
        win_to_unix_path "$ANDROID_HOME"
        return
    fi
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$(win_to_unix_path "$ANDROID_SDK_ROOT")" ]; then
        win_to_unix_path "$ANDROID_SDK_ROOT"
        return
    fi
    local local_sdk="/c/Users/${USERNAME:-$USER}/AppData/Local/Android/Sdk"
    if [ -d "$local_sdk" ]; then
        echo "$local_sdk"
        return
    fi
    echo ""
}

# Auto-detect JDK from common Windows locations.
detect_java_home() {
    if [ -n "${JAVA_HOME:-}" ] && [ -d "$(win_to_unix_path "$JAVA_HOME")" ]; then
        win_to_unix_path "$JAVA_HOME"
        return
    fi

    local candidates=(
        "/c/Program Files/Android/Android Studio/jbr"
        "/c/Program Files/Eclipse Adoptium/jdk-21"
        "/c/Program Files/Java/jdk-21"
        "/c/Program Files/Microsoft/jdk-21"
    )

    for c in "${candidates[@]}"; do
        if [ -d "$c" ]; then
            echo "$c"
            return
        fi
    done

    # Fallback: choose first jdk-21* folder if present.
    local found
    found=$(find "/c/Program Files" -maxdepth 3 -type d -iname "jdk-21*" 2>/dev/null | head -n 1 || true)
    if [ -n "$found" ]; then
        echo "$found"
        return
    fi

    echo ""
}

ANDROID_HOME_DETECTED="$(detect_android_home)"
JAVA_HOME_DETECTED="$(detect_java_home)"

if [ -z "$ANDROID_HOME_DETECTED" ]; then
    echo "✗ Android SDK not found."
    echo "Install Android Studio, then make sure this folder exists:"
    echo "  C:\\Users\\$USERNAME\\AppData\\Local\\Android\\Sdk"
    echo "Or set ANDROID_HOME before running this script."
    exit 1
fi

if [ -z "$JAVA_HOME_DETECTED" ]; then
    echo "✗ JDK 21 not found."
    echo "Install JDK 21 or Android Studio, then set JAVA_HOME if needed."
    echo "Example in Git Bash:"
    echo "  export JAVA_HOME='/c/Program Files/Android/Android Studio/jbr'"
    exit 1
fi

export ANDROID_HOME="$ANDROID_HOME_DETECTED"
export ANDROID_SDK_ROOT="$ANDROID_HOME_DETECTED"
export JAVA_HOME="$JAVA_HOME_DETECTED"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"

cd "$PROJECT_DIR"

cleanup() {
    echo ""
    echo "▶ Cleaning up patched files..."
    for f in \
        .gitignore \
        android/app/src/main/java/tf/monochrome/music/BackgroundAudioPlugin.java
    do
        if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
            git checkout -- "$f" 2>/dev/null || true
        fi
    done

    rm -rf \
        index.html package.json package-lock.json bun.lock bun.lockb \
        vite.config.ts vite-plugin-auth-gate.js vite-plugin-blob.ts \
        vite-plugin-svg-use.ts vite-plugin-upload.js \
        styles.css stream-stub.js tsconfig.json tsconfig-eslint.json \
        eslint.config.js lhci.yml nginx.conf \
        js/ src/ public/ assets/ functions/ images/ dist/ node_modules/ \
        .npmrc .prettierrc .stylelintrc.json .htmlhintrc .gitmodules \
        .dockerignore .vscode/ .wrangler/ \
        android/build.gradle android/capacitor.settings.gradle \
        android/gradle.properties android/settings.gradle android/variables.gradle \
        android/gradlew android/gradlew.bat android/gradle/ android/.gitignore
    rm -rf node_modules/@capgo/capacitor-media-session 2>/dev/null || true
    echo "  ✓ Upstream web app files removed."
}
trap cleanup EXIT

echo "══════════════════════════════════════════"
echo "  Fabiodalez Music — Android Build for Windows"
echo "══════════════════════════════════════════"
echo "Project:      $PROJECT_DIR"
echo "JAVA_HOME:    $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"

echo ""
echo "▶ Checking required tools..."
command -v git >/dev/null || { echo "✗ git not found"; exit 1; }
command -v npm >/dev/null || { echo "✗ npm not found. Install Node.js."; exit 1; }
command -v npx >/dev/null || { echo "✗ npx not found. Install Node.js."; exit 1; }
command -v java >/dev/null || { echo "✗ java not found in JAVA_HOME/bin."; exit 1; }
echo "  ✓ Tools found."

if ! git remote get-url upstream >/dev/null 2>&1; then
    echo "▶ Adding upstream remote..."
    git remote add upstream https://github.com/monochrome-music/monochrome.git
fi

echo ""
echo "▶ Pulling latest from upstream/main..."
cleanup 2>/dev/null || true
git fetch upstream

UPSTREAM_SHA=$(git rev-parse upstream/main)
SYNC_FILE="$PROJECT_DIR/.upstream-sync-sha"
LAST_SHA=$(cat "$SYNC_FILE" 2>/dev/null || echo "")

if [ "$UPSTREAM_SHA" = "$LAST_SHA" ] && [ -f index.html ]; then
    echo "  Already up to date ($(git rev-parse --short upstream/main))."
    read -p "  Build anyway? (y/N) " -n 1 -r
    echo
    [[ ! $REPLY =~ ^[Yy]$ ]] && exit 0
else
    N=$(git rev-list --count "${LAST_SHA:-upstream/main^}..upstream/main" 2>/dev/null || echo "?")
    echo "  $N new commits. Extracting web app from upstream/main..."
    git archive upstream/main | tar -x \
        --exclude='capacitor.config.ts' \
        --exclude='README.md' \
        --exclude='android/app' \
        --exclude='android/android-service.js' \
        --exclude='android/fm-logger.js' \
        --exclude='android/capacitor-cordova-android-plugins' \
        --exclude='ios' \
        --exclude='extension' \
        --exclude='docker' \
        --exclude='.devcontainer' \
        --exclude='.github' \
        --exclude='CONTRIBUTING.md' \
        --exclude='DOCKER.md' \
        --exclude='INSTANCES.md' \
        --exclude='THEME_GUIDE.md' \
        --exclude='license'
    echo "$UPSTREAM_SHA" > "$SYNC_FILE"
    echo "  ✓ Updated to $(git rev-parse --short upstream/main)."
fi

# Upstream package.json may reference an invalid sourcemap-codec override.
if grep -q '"sourcemap-codec": "\^1.4.14"' package.json; then
    python - <<'PYEOF'
from pathlib import Path
p = Path('package.json')
s = p.read_text(encoding='utf-8')
s = s.replace('"sourcemap-codec": "^1.4.14"', '"sourcemap-codec": "^1.4.8"')
p.write_text(s, encoding='utf-8')
PYEOF
    echo "  ✓ package.json: sourcemap-codec override 1.4.14 -> 1.4.8"
fi

echo ""
echo "▶ Installing dependencies..."
npm install 2>&1 | tail -3
npm install --save @capacitor/status-bar @capacitor/splash-screen 2>&1 | tail -3
echo "  ✓ Done."

# Upstream imports @capgo/capacitor-media-session. The Android wrapper uses
# AudioForegroundService instead, so keep this as a build-time no-op shim until
# we replace it with a real bridge.
SHIM_DIR="node_modules/@capgo/capacitor-media-session"
rm -rf "$SHIM_DIR"
mkdir -p "$SHIM_DIR"
cat > "$SHIM_DIR/index.js" <<'SHIMEOF'
export const MediaSession = {
    setActionHandler: async () => {},
    setMetadata: async () => {},
    setPlaybackState: async () => {},
    setPositionState: async () => {},
};
SHIMEOF
cat > "$SHIM_DIR/package.json" <<'SHIMEOF'
{"name":"@capgo/capacitor-media-session","version":"0.0.0-shim","main":"index.js","module":"index.js","type":"module"}
SHIMEOF
echo "  ✓ @capgo/capacitor-media-session shimmed."

echo ""
echo "▶ Patching for Android build..."
python - <<'PYEOF'
import os

PROJECT_DIR = os.environ.get("PROJECT_DIR", os.getcwd())


def read(path):
    with open(os.path.join(PROJECT_DIR, path), "r", encoding="utf-8") as f:
        return f.read()


def write(path, content):
    with open(os.path.join(PROJECT_DIR, path), "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def patch(path, before, after, label):
    src = read(path)
    if before not in src:
        print("  ! " + label + ": pattern not found, skipping")
        return False
    write(path, src.replace(before, after, 1))
    print("  + " + label)
    return True

# index.html: logger first, Android service last, branding, network hints.
index = read("index.html")
if './js/fm-logger.js' not in index:
    index = index.replace('</head>', '<script src="./js/fm-logger.js"></script></head>', 1)
if './js/android-service.js' not in index:
    index = index.replace('</body>', '<script type="module" src="./js/android-service.js"></script></body>', 1)
index = index.replace('<span>Monochrome</span>', '<span>Fabiodalez</span>', 1)
if 'preconnect" href="https://api.tidal.com"' not in index:
    index = index.replace(
        '<link rel="preconnect" href="https://resources.tidal.com" crossorigin />',
        '<link rel="preconnect" href="https://resources.tidal.com" crossorigin />\n'
        '        <link rel="preconnect" href="https://api.tidal.com" crossorigin />\n'
        '        <link rel="preconnect" href="https://auth.monochrome.tf" crossorigin />\n'
        '        <link rel="preconnect" href="https://esm.sh" crossorigin />\n'
        '        <link rel="preconnect" href="https://audio-proxy.binimum.org" crossorigin />\n'
        '        <link rel="preconnect" href="https://tidal-uptime.geeked.wtf" crossorigin />\n'
        '        <link rel="dns-prefetch" href="https://streams.tidal.com" />\n'
        '        <link rel="dns-prefetch" href="https://cdn.tidal.com" />\n'
        '        <link rel="dns-prefetch" href="https://manifests.tidal.com" />\n'
        '        <link rel="dns-prefetch" href="https://eu-central.monochrome.tf" />\n'
        '        <link rel="dns-prefetch" href="https://us-west.monochrome.tf" />\n'
        '        <link rel="dns-prefetch" href="https://hifi-api.kennyy.com.br" />',
        1,
    )
write("index.html", index)
print("  + index.html: Android scripts, brand, CDN/API preconnect")

patch(
    "js/storage.js",
    """                    streaming: [
                         { url: 'https://hifi.geeked.wtf', version: '2.7' },""",
    """                    streaming: [
                         { url: 'https://eu-central.monochrome.tf', version: '2.10' },
                         { url: 'https://us-west.monochrome.tf', version: '2.10' },
                         { url: 'https://hifi-api.kennyy.com.br', version: '2.10' },
                         { url: 'https://hifi.geeked.wtf', version: '2.7' },""",
    "storage.js: add live streaming fallbacks",
)

patch("js/app.js", "}, 3000);", "}, 800);", "app.js: search debounce 3000ms -> 800ms")

patch(
    "js/ui.js",
    """            if (finalAlbums.length === 0 && finalTracks.length > 0) {
                const albumMap = new Map();
                finalTracks.forEach((track) => {
                    if (track.album && !albumMap.has(track.album.id)) {
                        albumMap.set(track.album.id, track.album);
                    }
                });
                finalAlbums = Array.from(albumMap.values());
            }""",
    """            if (finalAlbums.length === 0 && finalTracks.length > 0) {
                const albumMap = new Map();
                finalTracks.forEach((track) => {
                    if (track.album && !albumMap.has(track.album.id)) {
                        albumMap.set(track.album.id, track.album);
                    }
                });
                finalAlbums = Array.from(albumMap.values());
            }

            // Enrich albums that have no artist or cover from track data.
            if (finalAlbums.length > 0 && finalTracks.length > 0) {
                const trackInfoMap = new Map();
                finalTracks.forEach((track) => {
                    if (track.album && track.album.id && !trackInfoMap.has(track.album.id)) {
                        trackInfoMap.set(track.album.id, {
                            artist: track.artist || (track.artists && track.artists[0]) || null,
                            cover: (track.album && track.album.cover) || null,
                        });
                    }
                });
                finalAlbums.forEach((album) => {
                    const info = trackInfoMap.get(album.id);
                    if (!info) return;
                    if (!album.artist && !album.artists?.length && info.artist) {
                        album.artist = info.artist;
                        album.artists = [info.artist];
                    }
                    if (!album.cover && info.cover) {
                        album.cover = info.cover;
                    }
                });
            }""",
    "ui.js: enrich albums missing artist/cover from tracks",
)

patch(
    "js/cache.js",
    "        this.ttl = options.ttl || 1000 * 60 * 30;",
    """        this.ttl = options.ttl || 1000 * 60 * 30;
        this.ttlByType = {
            search_all: 1000 * 60 * 10,
            search_tracks: 1000 * 60 * 10,
            search_artists: 1000 * 60 * 60,
            search_albums: 1000 * 60 * 30,
            search_playlists: 1000 * 60 * 60,
            search_videos: 1000 * 60 * 15,
        };""",
    "cache.js: per-type TTL map",
)

patch(
    "js/cache.js",
    """    generateKey(type, params) {
        const paramString = typeof params === 'object' ? JSON.stringify(params) : String(params);
        return `${type}:${paramString}`;
    }""",
    """    generateKey(type, params) {
        let normalized = params;
        if (typeof params === 'string' && typeof type === 'string' && type.startsWith('search')) {
            normalized = params
                .trim()
                .toLowerCase()
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '');
        }
        const paramString = typeof normalized === 'object' ? JSON.stringify(normalized) : String(normalized);
        return `${type}:${paramString}`;
    }""",
    "cache.js: query normalization",
)

patch(
    "js/cache.js",
    """    async get(type, params) {
        const key = this.generateKey(type, params);

        if (this.memoryCache.has(key)) {
            const cached = this.memoryCache.get(key);
            if (Date.now() - cached.timestamp < this.ttl) {
                return cached.data;
            }
            this.memoryCache.delete(key);
        }

        if (this.db) {
            try {
                const cached = await this.getFromIndexedDB(key);
                if (cached && Date.now() - cached.timestamp < this.ttl) {""",
    """    async get(type, params) {
        const key = this.generateKey(type, params);
        const effectiveTtl = (this.ttlByType && this.ttlByType[type]) || this.ttl;

        if (this.memoryCache.has(key)) {
            const cached = this.memoryCache.get(key);
            if (Date.now() - cached.timestamp < effectiveTtl) {
                return cached.data;
            }
            this.memoryCache.delete(key);
        }

        if (this.db) {
            try {
                const cached = await this.getFromIndexedDB(key);
                if (cached && Date.now() - cached.timestamp < effectiveTtl) {""",
    "cache.js: per-type TTL lookup in get()",
)

api_replacements = [
    ("`/search/?q=${encodeURIComponent(query)}`", "`/search/?q=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
    ("`/search/?s=${encodeURIComponent(query)}`", "`/search/?s=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
    ("`/search/?al=${encodeURIComponent(query)}`", "`/search/?al=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
    ("`/search/?p=${encodeURIComponent(query)}`", "`/search/?p=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
    ("`/search/?v=${encodeURIComponent(query)}`", "`/search/?v=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
    ("`/search/?a=${encodeURIComponent(query)}`", "`/search/?a=${encodeURIComponent(query)}&limit=${(options && options.limit) || 50}`"),
]
api = read("js/api.js")
api_count = 0
for before, after in api_replacements:
    if before in api and after not in api:
        api = api.replace(before, after, 1)
        api_count += 1
write("js/api.js", api)
print(f"  + api.js: Android search limit=50 replacements ({api_count})")

patch(
    "vite.config.ts",
    "handler: 'CacheFirst',\n                            options: {\n                                cacheName: 'media',",
    "handler: 'NetworkOnly',\n                            options: {\n                                cacheName: 'media',",
    "vite.config.ts: workbox audio/video CacheFirst -> NetworkOnly",
)

hifi = read("js/HiFi.ts")
original_hifi = hifi
hifi = hifi.replace("artists,artists.profileArt,", "artists,")
hifi = hifi.replace(",artists.profileArt", "")
hifi = hifi.replace("limit: 100", "limit: 50")
hifi = hifi.replace("limit=100", "limit=50")
hifi = hifi.replace("limit=${100}", "limit=${50}")
hifi = hifi.replace(
    "include: 'albums,albums.coverArt,tracks,tracks.albums,biography,profileArt',",
    "include: 'albums,albums.coverArt,tracks,tracks.albums,tracks.albums.coverArt,biography,profileArt',",
)
if hifi != original_hifi:
    write("js/HiFi.ts", hifi)
    print("  + HiFi.ts: safer search includes and limit=50")
else:
    print("  ! HiFi.ts: no search stability changes applied")

print("  ✓ Upstream JS optimizations applied.")
PYEOF

echo "  ✓ Android build patches applied."

cp "$PROJECT_DIR/android/android-service.js" js/android-service.js
mkdir -p public/js
cp "$PROJECT_DIR/android/fm-logger.js" public/js/fm-logger.js
echo "  ✓ android-service.js + fm-logger.js copied."

echo ""
echo "▶ Building web app..."
npx vite build 2>&1 | tail -3
echo "  ✓ Web build complete."

echo ""
echo "▶ Syncing to Android..."
npx cap sync android 2>&1 | tail -2
echo "  ✓ Synced."

if [ -f "$PROJECT_DIR/android/app/src/main/res/drawable/splash.png" ] && \
   [ -f "$PROJECT_DIR/android/app/src/main/res/drawable/splash.xml" ]; then
    rm "$PROJECT_DIR/android/app/src/main/res/drawable/splash.png"
    echo "  ✓ Removed duplicate splash.png (keeping splash.xml)."
fi

BGPLUGIN="$PROJECT_DIR/android/app/src/main/java/tf/monochrome/music/BackgroundAudioPlugin.java"
if [ -f "$BGPLUGIN" ] && grep -q '`@PluginMethod`' "$BGPLUGIN"; then
    python - <<'PYEOF'
from pathlib import Path
p = Path('android/app/src/main/java/tf/monochrome/music/BackgroundAudioPlugin.java')
s = p.read_text(encoding='utf-8')
s = s.replace('`@PluginMethod`', '@PluginMethod')
p.write_text(s, encoding='utf-8', newline='\n')
PYEOF
    echo "  ✓ Stripped Kotlin backticks from BackgroundAudioPlugin.java."
fi

echo ""
echo "▶ Building APK..."
cd "$PROJECT_DIR/android"

if [ -f ./gradlew ]; then
    bash ./gradlew assembleDebug -q
elif [ -f ./gradlew.bat ]; then
    ./gradlew.bat assembleDebug -q
else
    echo "✗ Gradle wrapper not found. npx cap sync android should have created it."
    exit 1
fi

cd "$PROJECT_DIR"

if [ -f "$APK_OUTPUT" ]; then
    cp "$APK_OUTPUT" "$APK_COPY"
    if [ -f "$APK_COPY" ]; then
        SIZE=$(du -h "$APK_COPY" | awk '{print $1}')
        echo "  ✓ APK built (${SIZE})"
        echo ""
        echo "══════════════════════════════════════════"
        echo "  APK: $APK_COPY"
        echo "══════════════════════════════════════════"
    else
        echo "  ✗ Failed to copy APK to $APK_COPY"
        exit 1
    fi
else
    echo "  ✗ Build failed — no APK at $APK_OUTPUT"
    exit 1
fi

# Build with Android Studio on Windows

This is the Android Studio option for Fabiodalez Music / Monochrome Android wrapper.

Use this when you want to open the project in Android Studio and build the APK from the IDE instead of using only the terminal.

## Requirements

Install these first:

1. Git for Windows
2. Node.js LTS
3. Android Studio
4. Python 3 for Windows

Android Studio should install the Android SDK in this default location:

```text
C:\Users\flore\AppData\Local\Android\Sdk
```

The Windows build script can also use Android Studio's bundled Java runtime:

```text
C:\Program Files\Android\Android Studio\jbr
```

## Folder layout

Keep both repositories beside each other:

```text
C:\Users\flore\Documents\Projects\
├── monochrome\
└── Monochrome-Android-APK\
```

If `monochrome` does not exist yet, open Git Bash in `C:\Users\flore\Documents\Projects` and run:

```bash
git clone https://github.com/monochrome-music/monochrome.git
```

## Step 1 — Update wrapper repo

Open Git Bash:

```bash
cd /c/Users/flore/Documents/Projects/Monochrome-Android-APK
git pull
```

## Step 2 — Install wrapper into Monochrome

Still in Git Bash:

```bash
bash install-windows.sh ../monochrome
```

This copies the Android wrapper files into the Monochrome web app clone.

## Step 3 — Prepare Android Studio project

Go to the Monochrome clone:

```bash
cd ../monochrome
bash build-android-windows.sh --android-studio
```

This will:

- Fetch latest upstream Monochrome
- Install npm dependencies
- Apply Android wrapper patches
- Build the web assets
- Run Capacitor sync
- Write `android/local.properties`
- Leave the generated Android project in place

It does **not** clean up generated Android files, because Android Studio needs them.

## Step 4 — Open in Android Studio

Open Android Studio, then choose:

```text
File > Open
```

Select this folder:

```text
C:\Users\flore\Documents\Projects\monochrome\android
```

Do not open `Monochrome-Android-APK` as the Android Studio project. That repo is the wrapper source. The actual Android Studio project is `monochrome\android`.

## Step 5 — Build APK in Android Studio

In Android Studio, wait for Gradle Sync to finish.

Then build:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

The APK should be created here:

```text
C:\Users\flore\Documents\Projects\monochrome\android\app\build\outputs\apk\debug\app-debug.apk
```

You can also run the app directly to a connected phone from Android Studio using the green Run button.

## Optional — Prepare using double-click batch file

After running `install-windows.sh`, the Monochrome folder will contain:

```text
prepare-android-studio-windows.bat
```

You can double-click it from Windows Explorer, or run it from Command Prompt inside the `monochrome` folder:

```bat
prepare-android-studio-windows.bat
```

## Troubleshooting

### Android SDK not found

In Git Bash, set Android SDK manually:

```bash
export ANDROID_HOME="/c/Users/flore/AppData/Local/Android/Sdk"
export ANDROID_SDK_ROOT="/c/Users/flore/AppData/Local/Android/Sdk"
bash build-android-windows.sh --android-studio
```

### JDK not found

Use Android Studio's bundled Java runtime:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
bash build-android-windows.sh --android-studio
```

### Python not found

Install Python 3 for Windows and enable the option:

```text
Add python.exe to PATH
```

Then reopen Git Bash and run the script again.

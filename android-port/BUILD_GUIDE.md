# SRB2 Android Port - Build Guide

## Overview
This is an Android port of Sonic Robo Blast 2 v2.2.15 using SDL2 as the 
platform abstraction layer. It includes touch controls with a virtual D-pad
and action buttons.

## Prerequisites

### 1. Install Android Studio
Download from: https://developer.android.com/studio

### 2. Install SDK Components
Open Android Studio > Settings > SDK Manager, then install:
- **SDK Platforms**: Android 14.0 (API 34)
- **SDK Tools** (check "Show Package Details"):
  - Android NDK (Side by side) — any recent version (e.g., 26.x or 27.x)
  - CMake 3.22.1
  - Android SDK Build-Tools 34.x
  - Android SDK Platform-Tools

### 3. Git (already installed if you cloned this repo)

---

## Build Steps

### Step 1: Clone SDL2 Dependencies
Open a terminal in this `android-port` folder and run:

```batch
setup_and_build.bat setup
```

Or manually:
```batch
cd app\src\main\jni
git clone --branch release-2.28.5 --depth 1 https://github.com/libsdl-org/SDL.git SDL2
git clone --branch release-2.6.3 --depth 1 https://github.com/libsdl-org/SDL_mixer.git SDL2_mixer
```

### Step 2: Open in Android Studio
1. Open Android Studio
2. File > Open > Navigate to this `android-port` folder
3. Wait for Gradle sync to complete (first time takes a while)
4. If prompted about Gradle version, accept the suggested update

### Step 3: Build
- Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
- Or press Shift+F10 to build and run on a connected device
- Or from terminal: `gradlew.bat assembleDebug`

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Game Data Setup

SRB2 needs its data files (.pk3) to run. You have two options:

### Option A: Bundle in APK (larger APK, simpler)
Copy these files to `app/src/main/assets/srb2data/`:
- `srb2.pk3`
- `zones.pk3`
- `characters.pk3`
- `music.pk3`

They'll be automatically extracted on first launch.

### Option B: Push to Device Separately (smaller APK)
After installing the APK, push files via ADB:
```batch
adb push "C:\Users\peachfan\SRB2 v2.2\srb2.pk3" /sdcard/Android/data/org.srb2.android/files/SRB2/
adb push "C:\Users\peachfan\SRB2 v2.2\zones.pk3" /sdcard/Android/data/org.srb2.android/files/SRB2/
adb push "C:\Users\peachfan\SRB2 v2.2\characters.pk3" /sdcard/Android/data/org.srb2.android/files/SRB2/
adb push "C:\Users\peachfan\SRB2 v2.2\music.pk3" /sdcard/Android/data/org.srb2.android/files/SRB2/
```

### Addons
Push addon files to the same directory:
```batch
adb push "C:\Users\peachfan\SRB2 v2.2\addons\L_AdminToolsPlus_v1.2.lua" /sdcard/Android/data/org.srb2.android/files/SRB2/addons/
```

---

## Touch Controls Layout

The on-screen controls in landscape mode:

```
[START] [ESC]              (top right)

                           [TOSS]
                     [FIRE]      [SPIN]
  (D-PAD)                 [JUMP]
```

- **D-Pad** (left side): Movement — supports 8 directions
- **JUMP**: Space bar — Jump
- **SPIN**: Left Shift — Spin/spindash
- **FIRE**: Left Ctrl — Fire rings / custom action
- **TOSS**: W key — Toss flag (CTF)
- **START**: Enter key
- **ESC**: Escape — Pause/menu

Bluetooth/USB gamepads are also supported via SDL2's controller handling.

---

## Project Structure

```
android-port/
├── build.gradle              # Root Gradle build
├── settings.gradle           # Project settings
├── gradle.properties         # Gradle JVM settings
├── setup_and_build.bat       # Automated setup script
├── app/
│   ├── build.gradle          # App module build config
│   ├── proguard-rules.pro    # ProGuard keep rules
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/srb2data/  # Place .pk3 files here
│       ├── java/org/srb2/android/
│       │   ├── SRB2Activity.java      # Main activity (extends SDLActivity)
│       │   └── TouchControlsView.java # On-screen touch controls
│       ├── jni/
│       │   ├── CMakeLists.txt         # Native build (compiles SRB2 + SDL2)
│       │   ├── srb2_android.c         # Android JNI helpers
│       │   ├── SDL2/                  # SDL2 source (cloned in setup)
│       │   └── SDL2_mixer/            # SDL_mixer source (cloned in setup)
│       └── res/                       # Android resources & icons
```

---

## Architecture

This port uses **SDL2** as the platform layer, meaning:
- SRB2's existing `src/sdl/` backend handles video, audio, and input
- SDL2's Android Java code (`SDLActivity`) manages the GL surface and audio
- Touch controls are rendered as a transparent overlay and send SDL key events
- OpenGL ES 2.0 is used for hardware-accelerated rendering
- Audio goes through SDL2_mixer → Android AudioTrack

---

## Troubleshooting

### "SDL2 source not found"
Run `setup_and_build.bat setup` or manually clone SDL2 into `app/src/main/jni/SDL2/`.

### Build fails with NDK errors
Make sure NDK is installed via Android Studio SDK Manager. Check that `local.properties` has the correct `sdk.dir` path.

### Game crashes on launch
Make sure all required `.pk3` files are in the game data directory. Check `adb logcat -s SRB2` for error messages.

### Black screen
The game may take a moment to load. Check logcat for "SRB2" tag messages.

### Touch controls not visible
Make sure `SRB2Activity` is the launch activity (check AndroidManifest.xml).

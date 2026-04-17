# SRB2 Android Port

An unofficial Android port of [Sonic Robo Blast 2 (SRB2)](https://srb2.org/) v2.2.15, the 3D Sonic the Hedgehog fangame built on a modified Doom Legacy engine.

## Features

- **Full SRB2 v2.2.15** gameplay on Android devices
- **Touch controls** with virtual D-pad, action buttons (Jump, Spin, Fire, Toss Flag), and camera drag
- **Online multiplayer** with master server support (server browser, joining games, downloading addons)
- **Lua addon support** for community mods
- **ARM64 (arm64-v8a)** native build

## Screenshots

*(Coming soon)*

## Download

Download the latest APK from the [Releases](../../releases) page.

### Requirements

- Android 5.0 (API 21) or higher
- ARM64 device (most modern Android phones)
- ~150 MB storage for game data

### Installation

1. Download the `.apk` file from Releases
2. Enable "Install from unknown sources" in your Android settings if prompted
3. Open the APK and install
4. Launch SRB2 — game data will be extracted on first run

## Building from Source

### Prerequisites

- Android Studio or Android SDK with NDK
- CMake 3.16+
- JDK 17+
- Gradle 8.2+

### Build Steps

1. Clone the third-party libraries into `app/src/main/jni/`:

```bash
cd android-port/app/src/main/jni
git clone https://github.com/libsdl-org/SDL.git -b release-2.28.x SDL2
git clone https://github.com/libsdl-org/SDL_mixer.git -b release-2.6.x SDL2_mixer
git clone https://github.com/curl/curl.git -b curl-8_5_0 curl
git clone https://github.com/Mbed-TLS/mbedtls.git -b v3.5.1 mbedtls
```

2. Build the APK:

```bash
cd android-port
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Configuration

- **Target ABI:** arm64-v8a
- **Min SDK:** 21 (Android 5.0)
- **Compile SDK:** 36
- **NDK:** Side-by-side installed via SDK Manager
- **Libraries:** SDL2, SDL2_mixer, libpng, curl (with mbedTLS), zlib

## Android-Specific Changes

This port includes several modifications to make SRB2 work on Android:

- **Touch controls overlay** — Java-based virtual gamepad with D-pad, action buttons, and camera drag zone
- **SSL via Java HttpsURLConnection** — Replaces curl+mbedTLS (which fails on Android) with JNI calls to Java's native HTTP stack for master server communication
- **Android-optimized key bindings** — Arrow keys mapped to movement (forward/backward/strafe) instead of camera, since the touch D-pad replaces the keyboard
- **FORTIFY_SOURCE disabled for Lua** — Prevents `SIGABRT` crashes when loading Lua addons on Android's strict libc
- **Accelerometer disabled** — `SDL_HINT_ACCELEROMETER_AS_JOYSTICK` set to `"0"` to prevent unwanted camera movement

## Controls

| Touch Control | Action |
|---|---|
| D-Pad (left side) | Move (forward/back/strafe) |
| Camera drag (center) | Look around |
| **A** button | Jump |
| **B** button | Spin |
| **C** button | Fire |
| **D** button | Toss Flag |
| **PAUSE** | Pause / Menu |
| **START** | Enter / Confirm |

The D-pad also navigates menus (up/down/left/right).

## Credits

### Original Game

**[Sonic Robo Blast 2](https://srb2.org/)** is developed by **[Sonic Team Junior (STJr)](https://github.com/STJr/SRB2)**.

- SRB2 is a fangame based on a modified version of [Doom Legacy](http://doomlegacy.sourceforge.net/)
- Original source code: [github.com/STJr/SRB2](https://github.com/STJr/SRB2)
- SRB2 is licensed under the **GNU General Public License v2.0**

### Disclaimer

Sonic Team Junior is in no way affiliated with SEGA or Sonic Team. We do not claim ownership of any of SEGA's intellectual property used in SRB2.

This is an **unofficial** community Android port and is not affiliated with or endorsed by Sonic Team Junior.

### Android Port

Ported to Android by **[@thegreatbbd-ux](https://github.com/thegreatbbd-ux)**.

### Third-Party Libraries

- [SDL2](https://www.libsdl.org/) — Simple DirectMedia Layer
- [SDL2_mixer](https://github.com/libsdl-org/SDL_mixer) — Audio mixer library
- [libpng](http://www.libpng.org/) — PNG image library
- [curl](https://curl.se/) + [mbedTLS](https://tls.mbed.org/) — HTTP/TLS (used as build dependency)
- [zlib](https://zlib.net/) — Compression library

## License

This project is licensed under the **GNU General Public License v2.0**, the same license as the original SRB2 source code. See [LICENSE](../LICENSE) for details.

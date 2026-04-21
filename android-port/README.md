# SRB2 Android Port

Unofficial Android port of [Sonic Robo Blast 2](https://srb2.org/) v2.2.15. I made this because I wanted to play SRB2 on my phone — the official version doesn't have Android support.

The full source code is right here in this repo. The Android-specific stuff is in this `android-port/` directory and the SRB2 engine source is in `src/`.

## Download

Grab the latest APK from the [Releases](../../releases) page.

**Requirements:**
- Android 5.0 or newer (Android 4.x is not supported)
- ARM64 device (basically any phone made after 2015)
- ~170 MB free space

**Install:** Download the APK, tap it, enable "Install unknown apps" if Android asks, then install.

## Known Limitations

- **ARM64 only** — 32-bit ARMv7 and x86 builds aren't included right now.
- **Android 4.x not supported** — Minimum is Android 5.0 (API 21).
- **Performance** — The OpenGL renderer runs through **gl4es**, a compatibility layer that translates OpenGL 1.x/2.x calls to OpenGL ES 2.0. It works well but may be slower than a native GLES port on lower-end devices. The software renderer (`-software`) is available via the in-game renderer settings if needed.

## Addons / Mods

After launching the game once, SRB2 creates its home folder at:

```
/storage/emulated/0/Android/data/org.srb2.android/files/SRB2/
```

Open any file manager (e.g. Files by Google), navigate to `Android/data/org.srb2.android/files/SRB2/`, and drop your `.pk3` or `.wad` addon files in the `addons/` subfolder inside. Then load them from the in-game addon menu.

> On Android 11 and newer, some file managers restrict access to `Android/data/`. If yours does, try Files by Google — it can open that folder. You can also use `adb push yourmod.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/addons/` if you have a PC handy.

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

The D-pad also navigates menus.

## Building from Source

### What you need

- Android Studio (or Android SDK + NDK + CMake 3.16+)
- JDK 17+
- Gradle 8.2+

### Steps

1. Clone the JNI dependencies into `app/src/main/jni/`:

```bash
cd android-port/app/src/main/jni
git clone https://github.com/libsdl-org/SDL.git -b release-2.28.x SDL2
git clone https://github.com/libsdl-org/SDL_mixer.git -b release-2.6.x SDL2_mixer
git clone https://github.com/curl/curl.git -b curl-8_5_0 curl
git clone https://github.com/Mbed-TLS/mbedtls.git -b v3.5.1 mbedtls
git clone https://github.com/ptitSeb/gl4es.git gl4es
```

> **gl4es** provides OpenGL 1.x/2.x compatibility over OpenGL ES 2.0. It's what makes the hardware renderer (OpenGL mode + 3D models) work on Android. The build will fail without it.

2. Grab the SRB2 v2.2.15 `.pk3` files from the [official release](https://github.com/STJr/SRB2/releases/tag/SRB2_release_2.2.15) and put them in `app/src/main/assets/srb2data/`.

3. Build:

```bash
cd android-port
./gradlew assembleRelease
```

APK ends up at `app/build/outputs/apk/release/`.

## Android-Specific Changes

A few things had to be changed to make SRB2 work on Android:

- **SSL**: curl + mbedTLS doesn't work cleanly on Android. Master server requests now go through Android's built-in `HttpsURLConnection` via JNI instead.
- **Touch controls**: Java-side overlay with a virtual D-pad, action buttons, and a camera drag zone on top of the SDL surface.
- **Lua crashes**: `FORTIFY_SOURCE` had to be disabled for Lua to stop `SIGABRT` crashes when loading addons on Android's strict libc.
- **Accelerometer disabled**: `SDL_HINT_ACCELEROMETER_AS_JOYSTICK` is set to `"0"` so the accelerometer doesn't mess with camera movement.
- **Key bindings**: Arrow keys are mapped to movement (not camera) since the touch D-pad handles movement and the camera drag zone handles looking around.

## Credits

**[Sonic Robo Blast 2](https://srb2.org/)** is made by **[Sonic Team Junior](https://github.com/STJr/SRB2)**, licensed under GPL v2.

Android port by **[@thegreatbbd-ux](https://github.com/thegreatbbd-ux)**.

This is an unofficial fan port. Sonic the Hedgehog is owned by SEGA. Not affiliated with or endorsed by Sonic Team Junior or SEGA.

### Third-Party Libraries

- [SDL2](https://www.libsdl.org/) — Simple DirectMedia Layer
- [SDL2_mixer](https://github.com/libsdl-org/SDL_mixer) — Audio mixer
- [libpng](http://www.libpng.org/) — PNG support
- [curl](https://curl.se/) + [mbedTLS](https://tls.mbed.org/) — HTTP/TLS (build dependency)
- [zlib](https://zlib.net/) — Compression

## License

GPL v2, same as the original SRB2. See [LICENSE](../LICENSE).

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

@echo off
REM ============================================================
REM SRB2 Android Port - Setup & Build Script
REM ============================================================
REM 
REM Prerequisites:
REM   1. Android Studio installed (https://developer.android.com/studio)
REM   2. Android SDK & NDK installed via Android Studio SDK Manager:
REM      - Android SDK Platform 36 (Android 16)
REM      - Android NDK (Side by side) - any recent version
REM      - CMake 3.22.1 (via SDK Manager > SDK Tools)
REM   3. Git installed
REM   4. Java JDK 17+ (comes with Android Studio)
REM
REM Usage:
REM   setup_and_build.bat          - Full setup + build
REM   setup_and_build.bat setup    - Only clone dependencies
REM   setup_and_build.bat build    - Only build (after setup)
REM ============================================================

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set JNI_DIR=%SCRIPT_DIR%app\src\main\jni

if "%1"=="build" goto :build
if "%1"=="setup" goto :setup

:setup
echo.
echo ========================================
echo  SRB2 Android Port - Setup
echo ========================================
echo.

REM Clone SDL2
if not exist "%JNI_DIR%\SDL2\CMakeLists.txt" (
    echo [1/5] Cloning SDL2 source...
    git clone --branch release-2.28.5 --depth 1 https://github.com/libsdl-org/SDL.git "%JNI_DIR%\SDL2"
    if errorlevel 1 (
        echo ERROR: Failed to clone SDL2
        exit /b 1
    )
    echo SDL2 cloned successfully.
) else (
    echo [1/5] SDL2 already present, skipping.
)

REM Clone SDL2_mixer
if not exist "%JNI_DIR%\SDL2_mixer\CMakeLists.txt" (
    echo [2/5] Cloning SDL2_mixer source...
    git clone --branch release-2.6.3 --depth 1 https://github.com/libsdl-org/SDL_mixer.git "%JNI_DIR%\SDL2_mixer"
    if errorlevel 1 (
        echo WARNING: Failed to clone SDL2_mixer. Audio will be basic.
    ) else (
        echo SDL2_mixer cloned successfully.
    )
) else (
    echo [2/5] SDL2_mixer already present, skipping.
)

REM Clone curl
if not exist "%JNI_DIR%\curl\CMakeLists.txt" (
    echo [3/5] Cloning curl source...
    git clone --branch curl-8_5_0 --depth 1 https://github.com/curl/curl.git "%JNI_DIR%\curl"
    if errorlevel 1 (
        echo WARNING: Failed to clone curl. Master server HTTPS may not work.
    ) else (
        echo curl cloned successfully.
    )
) else (
    echo [3/5] curl already present, skipping.
)

REM Clone mbedTLS (needed by curl for HTTPS)
if not exist "%JNI_DIR%\mbedtls\CMakeLists.txt" (
    echo [4/5] Cloning mbedTLS source...
    git clone --branch v3.5.1 --depth 1 https://github.com/Mbed-TLS/mbedtls.git "%JNI_DIR%\mbedtls"
    if errorlevel 1 (
        echo WARNING: Failed to clone mbedTLS.
    ) else (
        echo mbedTLS cloned successfully.
    )
) else (
    echo [4/5] mbedTLS already present, skipping.
)

REM Clone gl4es (OpenGL 1.x/2.x over OpenGL ES 2.0 - needed for HW renderer + 3D models)
if not exist "%JNI_DIR%\gl4es\CMakeLists.txt" (
    echo [5/5] Cloning gl4es source...
    git clone --depth 1 https://github.com/ptitSeb/gl4es.git "%JNI_DIR%\gl4es"
    if errorlevel 1 (
        echo ERROR: Failed to clone gl4es. OpenGL renderer and 3D models will not work.
        exit /b 1
    )
    echo gl4es cloned successfully.
) else (
    echo [5/5] gl4es already present, skipping.
)

echo.
echo Setup complete!
echo.

if "%1"=="setup" goto :done

:build
echo.
echo ========================================
echo  SRB2 Android Port - Build
echo ========================================
echo.

REM Check for local.properties
if not exist "%SCRIPT_DIR%local.properties" (
    echo Creating local.properties...
    echo You need to set your Android SDK path.
    echo.
    
    REM Try to auto-detect SDK location
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set SDK_DIR=%LOCALAPPDATA%\Android\Sdk
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set SDK_DIR=%USERPROFILE%\AppData\Local\Android\Sdk
    ) else (
        echo Could not auto-detect Android SDK. Please create local.properties manually:
        echo   sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
        exit /b 1
    )
    
    REM Replace backslashes for properties file
    set SDK_ESCAPED=!SDK_DIR:\=\\!
    echo sdk.dir=!SDK_ESCAPED!> "%SCRIPT_DIR%local.properties"
    echo SDK path set to: !SDK_DIR!
)

REM Check SDL2 is present
if not exist "%JNI_DIR%\SDL2\CMakeLists.txt" (
    echo ERROR: SDL2 not found. Run: setup_and_build.bat setup
    exit /b 1
)

REM Build using Gradle wrapper or system Gradle
if exist "%SCRIPT_DIR%gradlew.bat" (
    call "%SCRIPT_DIR%gradlew.bat" assembleRelease
) else (
    echo No Gradle wrapper found. Using system Gradle...
    echo If this fails, open the project in Android Studio instead.
    gradle assembleRelease
)

if errorlevel 1 (
    echo.
    echo Build failed! Try opening the project in Android Studio for better error messages.
    exit /b 1
)

echo.
echo ========================================
echo  BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK location:
echo   %SCRIPT_DIR%app\build\outputs\apk\release\app-release-unsigned.apk
echo.
echo To install on a connected device:
echo   adb install app\build\outputs\apk\release\app-release-unsigned.apk
echo.
echo IMPORTANT: You must copy game data to your device:
echo   adb push srb2.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push zones.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push characters.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push music.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo.

:done
endlocal

@echo off
REM ============================================================
REM SRB2 Android Port - Setup & Build Script
REM ============================================================
REM 
REM Prerequisites:
REM   1. Android Studio installed (https://developer.android.com/studio)
REM   2. Android SDK & NDK installed via Android Studio SDK Manager:
REM      - Android SDK Platform 34
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
    echo [1/2] Cloning SDL2 source...
    git clone --branch release-2.28.5 --depth 1 https://github.com/libsdl-org/SDL.git "%JNI_DIR%\SDL2"
    if errorlevel 1 (
        echo ERROR: Failed to clone SDL2
        exit /b 1
    )
    echo SDL2 cloned successfully.
) else (
    echo [1/2] SDL2 already present, skipping.
)

REM Clone SDL2_mixer
if not exist "%JNI_DIR%\SDL2_mixer\CMakeLists.txt" (
    echo [2/2] Cloning SDL2_mixer source...
    git clone --branch release-2.6.3 --depth 1 https://github.com/libsdl-org/SDL_mixer.git "%JNI_DIR%\SDL2_mixer"
    if errorlevel 1 (
        echo WARNING: Failed to clone SDL2_mixer. Audio will be basic.
    ) else (
        echo SDL2_mixer cloned successfully.
    )
) else (
    echo [2/2] SDL2_mixer already present, skipping.
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
    call "%SCRIPT_DIR%gradlew.bat" assembleDebug
) else (
    echo No Gradle wrapper found. Using system Gradle...
    echo If this fails, open the project in Android Studio instead.
    gradle assembleDebug
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
echo   %SCRIPT_DIR%app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on a connected device:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.
echo IMPORTANT: You must copy game data to your device:
echo   adb push srb2.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push zones.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push characters.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo   adb push music.pk3 /sdcard/Android/data/org.srb2.android/files/SRB2/
echo.

:done
endlocal

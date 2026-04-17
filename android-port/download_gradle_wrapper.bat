@echo off
REM Downloads the Gradle wrapper for use without Android Studio
REM Run this from the android-port directory

setlocal

set GRADLE_VERSION=8.2
set WRAPPER_JAR=gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/nickolay/gradle-wrapper-jar/refs/heads/master/gradle-wrapper.jar

if exist "%WRAPPER_JAR%" (
    echo Gradle wrapper already exists.
    goto :create_scripts
)

echo Downloading Gradle wrapper...
mkdir gradle\wrapper 2>nul

REM Use PowerShell to download
powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
if errorlevel 1 (
    echo Failed to download gradle-wrapper.jar
    echo.
    echo Alternative: Open this project in Android Studio - it handles Gradle automatically.
    exit /b 1
)

:create_scripts
REM Create gradlew.bat
echo Creating gradlew.bat...
(
echo @rem Gradle startup script for Windows
echo @rem Generated for SRB2 Android Port
echo @if "%%OS%%"=="Windows_NT" setlocal
echo set DIRNAME=%%~dp0
echo set APP_BASE_NAME=%%~n0
echo set APP_HOME=%%DIRNAME%%
echo set CLASSPATH=%%APP_HOME%%\gradle\wrapper\gradle-wrapper.jar
echo set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
echo @rem Find java.exe
echo set JAVA_EXE=java.exe
echo %%JAVA_EXE%% %%DEFAULT_JVM_OPTS%% -classpath "%%CLASSPATH%%" org.gradle.wrapper.GradleWrapperMain %%*
) > gradlew.bat

echo.
echo Gradle wrapper setup complete!
echo You can now run: gradlew.bat assembleDebug
echo.
echo Or better: Open this folder in Android Studio.

endlocal

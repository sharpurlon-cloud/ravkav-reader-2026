@echo off
REM ============================================================================
REM BUILD COMPLETE RAVKAV READER APP - FROM SCRATCH
REM ============================================================================

setlocal enabledelayedexpansion
cd /d "c:\Users\HP OMNIBOOK\Desktop\test\reader_app"

echo.
echo ================================================================================
echo   BUILD RAVKAV CARD READER v2.0 WITH DECRYPTION & MAC
echo ================================================================================
echo.

REM Check if gradle is installed
where gradle >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Gradle not found in PATH
    echo.
    echo Installing Gradle...

    REM Try to use gradle wrapper if exists
    if exist "gradlew.bat" (
        echo Using gradlew wrapper...
        set GRADLE_CMD=gradlew
    ) else (
        echo ERROR: Gradle wrapper not found
        echo Creating minimal gradle setup...
        goto :create_gradle
    )
) else (
    set GRADLE_CMD=gradle
)

:build_app
echo.
echo [STEP 1] Cleaning previous builds...
call %GRADLE_CMD% clean

echo.
echo [STEP 2] Assembling debug APK...
call %GRADLE_CMD% assemble

echo.
echo [STEP 3] Building release APK...
call %GRADLE_CMD% assemble release

REM Check if build was successful
if exist "build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ✓ DEBUG APK built successfully!
    echo   Location: build\outputs\apk\debug\app-debug.apk
    goto :install_app
) else if exist "build\outputs\apk\debug\reader_app-debug.apk" (
    echo.
    echo ✓ DEBUG APK built successfully!
    echo   Location: build\outputs\apk\debug\reader_app-debug.apk
    goto :install_app
) else (
    echo.
    echo ERROR: APK file not found after build
    echo Checking build output directory...
    if exist "build\outputs" (
        dir /s "build\outputs\apk"
    )
    goto :error
)

:install_app
echo.
echo [STEP 3] Installing APK to connected device...

REM Find APK
for /r "build\outputs\apk\debug" %%f in (*.apk) do (
    set APK_FILE=%%f
)

if defined APK_FILE (
    echo Installing: !APK_FILE!
    adb install -r "!APK_FILE!"

    if %errorlevel% equ 0 (
        echo.
        echo ✓ APK installed successfully!
        echo.
        echo Next steps:
        echo   1. Open the app on your device
        echo   2. Place a charged card to read
        echo   3. Data will be decrypted and displayed
        echo   4. MAC will be calculated and shown
        goto :success
    ) else (
        echo.
        echo ERROR: Failed to install APK
        echo Make sure device is connected: adb devices
        goto :error
    )
) else (
    echo ERROR: No APK file found
    goto :error
)

:create_gradle
echo.
echo Creating gradle wrapper...
REM This is a fallback if gradle wrapper doesn't exist
echo ERROR: Cannot create gradle wrapper from batch script
echo Please run: gradle wrapper in the project directory
goto :error

:success
echo.
echo ================================================================================
echo BUILD COMPLETE - APP READY TO USE
echo ================================================================================
exit /b 0

:error
echo.
echo ================================================================================
echo BUILD FAILED
echo ================================================================================
echo.
echo Troubleshooting:
echo   1. Ensure Java 11+ is installed: java -version
echo   2. Ensure Gradle is in PATH: gradle -version
echo   3. Ensure Android SDK is installed: adb version
echo   4. Ensure device is connected: adb devices
echo.
exit /b 1

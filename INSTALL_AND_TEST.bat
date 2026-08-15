@echo off
REM Simple installation and testing script
REM Usage: INSTALL_AND_TEST.bat <path-to-apk>

setlocal enabledelayedexpansion

echo.
echo ==================================================================
echo   RavKav Reader - Installation and Test Script
echo ==================================================================
echo.

REM Check if APK path is provided
if "%~1"=="" (
    echo Error: APK path required
    echo Usage: INSTALL_AND_TEST.bat "path\to\app.apk"
    exit /b 1
)

set "APK_PATH=%~1"

REM Check if APK exists
if not exist "%APK_PATH%" (
    echo Error: APK file not found: %APK_PATH%
    exit /b 1
)

echo [1] Checking device connection...
adb devices | findstr "device" > nul
if errorlevel 1 (
    echo Error: No device connected. Please connect your phone.
    exit /b 1
)
echo OK - Device found

echo.
echo [2] Installing APK...
echo Installing: %APK_PATH%
adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo Error: Installation failed
    exit /b 1
)
echo OK - APK installed

echo.
echo [3] Starting application...
adb shell am start -n "co.hopon.android.rkpos/co.hopon.android.rkpos2.Splash"
timeout /t 2 /nobreak
echo OK - App should be running

echo.
echo [4] Clearing logcat...
adb logcat -c

echo.
echo ==================================================================
echo   Next steps:
echo   1. Place an old charged RavKav card near NFC reader
echo   2. Wait for app to read (should show "Read successful" in logcat)
echo   3. Place a new blank card near NFC reader
echo   4. Select balance (50, 100, 150, etc)
echo   5. Wait for app to write (should show "Write successful")
echo ==================================================================
echo.

echo [5] Starting logcat monitoring (Press Ctrl+C to stop)...
adb logcat -s "EnhancedMainActivity:I" "MCKDecryptor:I" "CryptoAnalyzer:I" "CardPersonalizer:I"

echo.
echo Done!
pause

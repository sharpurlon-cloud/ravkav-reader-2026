@echo off
REM ============================================================================
REM Install Android SDK Command Line Tools
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ╔════════════════════════════════════════════════════════════════════╗
echo ║              ANDROID SDK INSTALLATION SCRIPT                       ║
echo ╚════════════════════════════════════════════════════════════════════╝
echo.

REM Check if running as admin
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: This script must run as Administrator
    echo Please right-click and select "Run as administrator"
    exit /b 1
)

echo [STEP 1] Checking requirements...
echo.

REM Check Java
echo Checking Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found
    echo Install Java from: https://adoptium.net/
    exit /b 1
)
echo  OK - Java found

REM Create SDK directory
set SDK_DIR=C:\Android\sdk
echo.
echo [STEP 2] Creating SDK directory...
if not exist "%SDK_DIR%" (
    mkdir "%SDK_DIR%"
    echo  Created: %SDK_DIR%
) else (
    echo  Already exists: %SDK_DIR%
)

REM Download command line tools
echo.
echo [STEP 3] Downloading Android SDK Command Line Tools...
echo.

set TOOLS_ZIP=%TEMP%\cmdline-tools.zip
echo Downloading to: %TOOLS_ZIP%

REM Using curl if available
where curl >nul 2>&1
if %errorlevel% equ 0 (
    echo Attempting download with curl...
    curl -o "%TOOLS_ZIP%" "https://dl.google.com/android/repository/commandlinetools-win-11.0_latest.zip"
) else (
    echo Attempting download with PowerShell...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('https://dl.google.com/android/repository/commandlinetools-win-11.0_latest.zip', '%TOOLS_ZIP%')"
)

if not exist "%TOOLS_ZIP%" (
    echo ERROR: Failed to download SDK
    echo Please download manually from:
    echo https://developer.android.com/studio/command-line/sdkmanager
    exit /b 1
)

echo  Download complete

REM Extract tools
echo.
echo [STEP 4] Extracting command line tools...

if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools"

REM Use PowerShell to extract (more reliable)
powershell -Command "Expand-Archive -Path '%TOOLS_ZIP%' -DestinationPath '%SDK_DIR%\cmdline-tools' -Force"

if %errorlevel% neq 0 (
    echo ERROR: Failed to extract
    exit /b 1
)

echo  Extraction complete

REM Verify extraction
if not exist "%SDK_DIR%\cmdline-tools\cmdline-tools\bin\sdkmanager.bat" (
    echo WARNING: sdkmanager not found in expected location
    echo Checking extraction...
    dir "%SDK_DIR%\cmdline-tools\"
)

REM Set environment variables
echo.
echo [STEP 5] Setting environment variables...

setx ANDROID_HOME "%SDK_DIR%"
echo  ANDROID_HOME = %SDK_DIR%

setx Path "%Path%;%SDK_DIR%\cmdline-tools\cmdline-tools\bin;%SDK_DIR%\platform-tools"
echo  Added to PATH

REM Accept licenses
echo.
echo [STEP 6] Accepting Android SDK licenses...
echo.

set SDKMANAGER=%SDK_DIR%\cmdline-tools\cmdline-tools\bin\sdkmanager.bat

echo y | "%SDKMANAGER%" --licenses >nul 2>&1
if %errorlevel% equ 0 (
    echo  Licenses accepted
) else (
    echo  WARNING: Could not auto-accept licenses
    echo  You may need to accept them manually
)

REM Install essential platforms and tools
echo.
echo [STEP 7] Installing SDK platforms and tools...
echo.

echo Installing Android 14 (API 34)...
"%SDKMANAGER%" "platforms;android-34" >nul 2>&1

echo Installing Build Tools 34.0.0...
"%SDKMANAGER%" "build-tools;34.0.0" >nul 2>&1

echo Installing Platform Tools...
"%SDKMANAGER%" "platform-tools" >nul 2>&1

echo Installing Emulator (optional)...
"%SDKMANAGER%" "emulator" >nul 2>&1

echo.
echo  Installation complete

REM Verify installation
echo.
echo [STEP 8] Verifying installation...
echo.

if exist "%SDK_DIR%\platforms\android-34\android.jar" (
    echo  ✓ Android SDK platform 34 found
) else (
    echo  ✗ Android SDK platform 34 NOT found
)

if exist "%SDK_DIR%\build-tools\34.0.0\aapt.exe" (
    echo  ✓ Build Tools 34.0.0 found
) else (
    echo  ✗ Build Tools 34.0.0 NOT found
)

if exist "%SDK_DIR%\platform-tools\adb.exe" (
    echo  ✓ Platform Tools (adb) found
) else (
    echo  ✗ Platform Tools (adb) NOT found
)

REM Summary
echo.
echo ╔════════════════════════════════════════════════════════════════════╗
echo ║                    INSTALLATION COMPLETE                           ║
echo ╚════════════════════════════════════════════════════════════════════╝
echo.
echo Summary:
echo  SDK Directory: %SDK_DIR%
echo  ANDROID_HOME: %ANDROID_HOME%
echo.
echo Next steps:
echo  1. Close and reopen Command Prompt / PowerShell
echo  2. Verify: adb version
echo  3. Build app: gradle assemble
echo.
echo Troubleshooting:
echo  If you see errors:
echo   1. Check internet connection
echo   2. Run this script again as Administrator
echo   3. Visit: https://developer.android.com/studio
echo.

REM Clean up
del /f "%TOOLS_ZIP%" >nul 2>&1

pause

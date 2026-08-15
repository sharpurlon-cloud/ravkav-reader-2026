@echo off
REM ========================================================================
REM RavKav Card Reader - Quick Setup & Test Script
REM ========================================================================
REM This script automates common ADB testing tasks

setlocal enabledelayedexpansion

echo.
echo ========================================================================
echo RavKav Card Reader - Enhanced Testing Suite
echo ========================================================================
echo.

:menu
echo.
echo Choose an option:
echo   1 - Check ADB connection
echo   2 - Uninstall old version
echo   3 - Install fresh APK
echo   4 - Launch application
echo   5 - View logcat (real-time)
echo   6 - Extract logcat to file
echo   7 - Clear logcat
echo   8 - Run full test sequence
echo   9 - Exit
echo.

set /p choice="Enter option (1-9): "

if "%choice%"=="1" goto check_adb
if "%choice%"=="2" goto uninstall
if "%choice%"=="3" goto install
if "%choice%"=="4" goto launch
if "%choice%"=="5" goto logcat_realtime
if "%choice%"=="6" goto logcat_file
if "%choice%"=="7" goto clear_logcat
if "%choice%"=="8" goto full_test
if "%choice%"=="9" goto exit
echo Invalid choice!
goto menu

:check_adb
echo.
echo Checking ADB connection...
adb devices
if %errorlevel% neq 0 (
    echo ERROR: ADB not found or no devices connected!
    echo Please ensure:
    echo   1. Android SDK Platform Tools are installed
    echo   2. Device is connected via USB
    echo   3. USB Debugging is enabled
) else (
    echo ADB connection OK!
)
pause
goto menu

:uninstall
echo.
echo Attempting to uninstall previous version...
adb shell su -c "pm uninstall com.unicapitalgroup.ravkavreader"
if %errorlevel% equ 0 (
    echo Uninstall successful!
) else (
    echo Note: App may not have been installed
)
pause
goto menu

:install
echo.
echo Installing RavKav Card Reader Enhanced Edition...
if not exist "RavKavCardReader_Enhanced_AUTH-2026-001.apk" (
    echo ERROR: APK not found in current directory!
    echo Please ensure RavKavCardReader_Enhanced_AUTH-2026-001.apk is in:
    echo %cd%
    pause
    goto menu
)
adb install "RavKavCardReader_Enhanced_AUTH-2026-001.apk"
if %errorlevel% equ 0 (
    echo Installation successful!
) else (
    echo Installation failed! Check error above.
)
pause
goto menu

:launch
echo.
echo Launching application...
adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity
if %errorlevel% equ 0 (
    echo App launched successfully!
    echo The app should now be running on your device.
) else (
    echo Failed to launch app!
)
pause
goto menu

:logcat_realtime
echo.
echo Displaying real-time logcat (RavKavReaderPoC only)...
echo Press Ctrl+C to stop...
echo.
adb logcat -s "RavKavReaderPoC" -v threadtime
pause
goto menu

:logcat_file
echo.
set timestamp=%date:~-4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set logfile=card_test_results_%timestamp%.txt
echo Extracting logcat to file: %logfile%
adb logcat -s "RavKavReaderPoC" -d > "%logfile%"
echo.
echo Logcat saved to: %cd%\%logfile%
echo File size:
for %%A in ("%logfile%") do echo %%~zA bytes
pause
goto menu

:clear_logcat
echo.
echo Clearing logcat buffer...
adb logcat -c
echo Logcat cleared!
pause
goto menu

:full_test
echo.
echo ========================================================================
echo FULL TEST SEQUENCE
echo ========================================================================
echo.
echo This will:
echo   1. Check ADB connection
echo   2. Uninstall old version
echo   3. Install fresh APK
echo   4. Launch application
echo   5. Prepare for card testing
echo.
pause

echo.
echo [1/5] Checking ADB connection...
adb devices
if %errorlevel% neq 0 (
    echo ERROR: ADB connection failed!
    pause
    goto menu
)

echo.
echo [2/5] Uninstalling old version...
adb shell su -c "pm uninstall com.unicapitalgroup.ravkavreader"

echo.
echo [3/5] Installing fresh APK...
if not exist "RavKavCardReader_Enhanced_AUTH-2026-001.apk" (
    echo ERROR: APK file not found!
    pause
    goto menu
)
adb install "RavKavCardReader_Enhanced_AUTH-2026-001.apk"
if %errorlevel% neq 0 (
    echo Installation failed!
    pause
    goto menu
)

echo.
echo [4/5] Clearing logcat buffer...
adb logcat -c

echo.
echo [5/5] Launching application...
adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity

echo.
echo ========================================================================
echo SETUP COMPLETE!
echo ========================================================================
echo.
echo Next steps:
echo   1. App is now running on your device
echo   2. You should see "Waiting for card..." message
echo   3. Hold card to NFC area to start testing
echo   4. Check logcat in real-time (option 5) or save to file (option 6)
echo.
echo When done testing, extract logcat to file for analysis.
echo.
pause
goto menu

:exit
echo.
echo Goodbye!
exit /b 0

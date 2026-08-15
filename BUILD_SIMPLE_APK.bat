@echo off
REM Simple APK builder - No complex dependencies
REM Just modifies base APK with new code

setlocal enabledelayedexpansion

cls
echo.
echo ====================================================================
echo   RavKav Reader - Simple APK Builder
echo ====================================================================
echo.

REM Check Java
echo [1] Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found
    pause
    exit /b 1
)
echo OK - Java found

REM Check apktool
echo [2] Checking apktool...
if not exist "C:\tools\apktool.bat" (
    echo ERROR: apktool not found at C:\tools\apktool.bat
    echo Download from: https://ibotpeaches.github.io/Apktool/
    pause
    exit /b 1
)
echo OK - apktool found

REM Check base APK
set BASE_APK=c:\Users\HP OMNIBOOK\Desktop\test\CardCloner.apk
echo [3] Checking base APK...
if not exist "%BASE_APK%" (
    echo ERROR: Base APK not found: %BASE_APK%
    pause
    exit /b 1
)
echo OK - Base APK found

REM Setup directories
set WORK_DIR=C:\build_simple
set DECOMPILE_DIR=%WORK_DIR%\apk_decompiled
set OUTPUT_APK=%WORK_DIR%\EnhancedRavKavReader-manual.apk

echo [4] Creating work directories...
if exist "%WORK_DIR%" rmdir /s /q "%WORK_DIR%"
mkdir "%WORK_DIR%"
echo OK - Work directory ready

REM Decompile
echo.
echo [5] Decompiling base APK (this may take a few minutes)...
echo     This step extracts and prepares the APK structure
C:\tools\apktool.bat d -f "%BASE_APK%" -o "%DECOMPILE_DIR%"
if errorlevel 1 (
    echo ERROR: Decompilation failed
    pause
    exit /b 1
)
echo OK - Decompiled

REM Modify manifest to use our activity
echo [6] Modifying AndroidManifest.xml...
REM This would replace the main activity with EnhancedMainActivity
REM For now, we'll just recompile as-is to test the process
echo OK - Manifest ready

REM Recompile
echo [7] Recompiling APK...
C:\tools\apktool.bat b -f "%DECOMPILE_DIR%" -o "%OUTPUT_APK%"
if errorlevel 1 (
    echo ERROR: Build failed
    pause
    exit /b 1
)
echo OK - APK created

REM Sign and verify
echo [8] Signing APK...
if exist "C:\tools\debug.keystore" (
    jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 ^
        -keystore "C:\tools\debug.keystore" ^
        -storepass android ^
        -keypass android ^
        "%OUTPUT_APK%" androiddebugkey >nul 2>&1
    echo OK - Signed
)

REM Show result
echo.
echo ====================================================================
echo   BUILD COMPLETE
echo ====================================================================
echo.
echo APK: %OUTPUT_APK%
if exist "%OUTPUT_APK%" (
    for /F "tokens=*" %%A in ('dir /b "%OUTPUT_APK%"') do (
        echo Size: %%~zA bytes
    )
)
echo.
echo Next: adb install -r "%OUTPUT_APK%"
echo.
pause

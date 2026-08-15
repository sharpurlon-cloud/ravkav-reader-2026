@echo off
REM ============================================
REM CARD CLONER APK BUILD SCRIPT
REM ============================================
REM
REM Complete solution for card cloning:
REM - Comprehensive CardCloner engine
REM - Full UI with step-by-step workflow
REM - Real-time logging
REM - Crypto analysis
REM

cd /d "%~dp0"

echo.
echo ========================================
echo CARD CLONER BUILD PROCESS
echo ========================================
echo.

REM Check for required tools
if not exist "apktool.jar" (
    echo ERROR: apktool.jar not found!
    pause
    exit /b 1
)

if not exist "test.keystore" (
    echo ERROR: test.keystore not found!
    pause
    exit /b 1
)

REM Step 1: Compile Java files
echo Step 1: Compiling Java files...
javac -d bin ^
    reader_app\src\com\unicapitalgroup\ravkavreader\*.java ^
    2>nul

if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!

REM Step 2: Create APK structure
echo.
echo Step 2: Creating APK structure...

REM Step 3: Build APK
echo.
echo Step 3: Building APK...

java -jar apktool.jar b reader_app -o card_cloner.apk

if not exist "card_cloner.apk" (
    echo APK build failed!
    pause
    exit /b 1
)

echo APK built successfully!

REM Step 4: Sign APK
echo.
echo Step 4: Signing APK...

jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 ^
    -keystore test.keystore -storepass 123456 -keypass 123456 ^
    card_cloner.apk testkey

echo APK signed successfully!

REM Step 5: Install
echo.
echo Step 5: Installing APK...

adb install card_cloner.apk

if %errorlevel% neq 0 (
    echo Installation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo APK installed: card_cloner.apk
echo.
echo USAGE:
echo 1. Tap old card to READ and ANALYZE
echo 2. Tap new blank card to CREATE FILES
echo 3. Tap new card to WRITE DATA
echo 4. Tap new card to VERIFY
echo.
echo Good luck!
echo.
pause

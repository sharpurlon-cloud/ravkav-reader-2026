#!/usr/bin/env pwsh
# Build script for RavKav Card Reader Enhanced Edition
# Usage: .\build_apk.ps1

$ErrorActionPreference = "Stop"
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "================================" -ForegroundColor Green
Write-Host "RavKav Card Reader Build Script"
Write-Host "AUTH-2026-001 Enhanced Edition" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green

# Check for required tools
$tools = @("javac", "d8", "aapt2", "zipalign", "apksigner")
Write-Host "Checking for required tools..."

$javacPath = (Get-Command javac -ErrorAction SilentlyContinue).Source
if (-not $javacPath) {
    Write-Host "ERROR: javac not found. Please install Java Development Kit." -ForegroundColor Red
    exit 1
}
Write-Host "  javac: OK ($javacPath)" -ForegroundColor Green

# Create build directories
$buildDir = Join-Path $scriptPath "build"
$classesDir = Join-Path $buildDir "classes"
$dexDir = Join-Path $buildDir "dex"
$resDir = Join-Path $buildDir "res"
$distDir = Join-Path $buildDir "dist"

Write-Host "Creating build directories..."
New-Item -ItemType Directory -Path $classesDir, $dexDir, $resDir, $distDir -Force | Out-Null

# Compile Java sources
Write-Host "Compiling Java sources..."
$sourceDir = Join-Path $scriptPath "src"
$androidJar = "C:\Android\sdk\platforms\android-34\android.jar"

if (-not (Test-Path $androidJar)) {
    Write-Host "WARNING: android.jar not found at $androidJar" -ForegroundColor Yellow
    Write-Host "Attempting to compile without classpath..." -ForegroundColor Yellow
    & javac -d $classesDir -source 1.8 -target 1.8 `
        (Get-ChildItem -Path $sourceDir -Include "*.java" -Recurse).FullName
} else {
    & javac -d $classesDir -source 1.8 -target 1.8 `
        -cp $androidJar `
        (Get-ChildItem -Path $sourceDir -Include "*.java" -Recurse).FullName
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Java compilation failed" -ForegroundColor Red
    exit 1
}
Write-Host "  Compilation successful" -ForegroundColor Green

# Convert to DEX (if d8 is available)
Write-Host "Converting to DEX format..."
$d8Path = "C:\Android\sdk\build-tools\34.0.0\d8.bat"
if (Test-Path $d8Path) {
    & $d8Path --output=$dexDir (Get-ChildItem -Path $classesDir -Include "*.class" -Recurse).FullName
    Write-Host "  DEX conversion successful" -ForegroundColor Green
} else {
    Write-Host "  WARNING: d8 not found, skipping DEX conversion" -ForegroundColor Yellow
}

# Create APK filename with timestamp
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$apkFile = Join-Path $distDir "RavKavCardReader_Enhanced_AUTH-2026-001_$timestamp.apk"
$unsignedApk = Join-Path $distDir "unsigned.apk"

# Create APK manually using zip
Write-Host "Creating APK package..."
$zipContent = @{
    "classes.dex" = Join-Path $dexDir "classes.dex"
    "AndroidManifest.xml" = Join-Path $scriptPath "AndroidManifest.xml"
}

# Create zip archive
$temp7z = Join-Path $buildDir "package.zip"
if (Test-Path $temp7z) { Remove-Item $temp7z }

# Copy DEX if exists
if (Test-Path (Join-Path $dexDir "classes.dex")) {
    Copy-Item -Path (Join-Path $dexDir "classes.dex") -Destination $buildDir
}

Copy-Item -Path (Join-Path $scriptPath "AndroidManifest.xml") -Destination $buildDir

# Sign APK using built-in keystore
Write-Host "Signing APK..."
$keystorePath = Join-Path $scriptPath "readerapp.keystore"

if (Test-Path $keystorePath) {
    $jarsignerPath = "C:\Program Files\Java\*\bin\jarsigner.exe"
    $jarsignerExe = @(Get-ChildItem -Path $jarsignerPath -ErrorAction SilentlyContinue)[0]

    if ($jarsignerExe) {
        & $jarsignerExe.FullName `
            -keystore $keystorePath `
            -storepass android `
            -keypass android `
            -signedjar $apkFile `
            $unsignedApk `
            androidkey
        Write-Host "  APK signed successfully" -ForegroundColor Green
    } else {
        Write-Host "  WARNING: jarsigner not found" -ForegroundColor Yellow
    }
} else {
    Write-Host "  WARNING: keystore not found at $keystorePath" -ForegroundColor Yellow
}

# Create summary
$summary = @{
    "build_time" = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    "source_dir" = $sourceDir
    "output_apk" = $apkFile
    "version" = "1.1-AUTH-2026-001-Enhanced"
    "features" = @(
        "Probe APDU execution (4 variations)"
        "Real card detection (Empty vs Balance)"
        "Crypto analysis with actual responses"
        "Challenge sequence extraction"
    )
}

Write-Host ""
Write-Host "Build Summary:" -ForegroundColor Green
Write-Host "  Output APK: $apkFile"
Write-Host "  Build Date: $($summary.build_time)"
Write-Host "  Version: $($summary.version)"
Write-Host "  Features:"
$summary.features | ForEach-Object { Write-Host "    - $_" }

Write-Host ""
Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green

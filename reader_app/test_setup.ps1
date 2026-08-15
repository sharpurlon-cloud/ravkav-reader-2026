# ========================================================================
# RavKav Card Reader - Enhanced Testing Suite (PowerShell)
# ========================================================================
# This script automates common ADB testing tasks for Windows

function Show-Menu {
    Write-Host ""
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host "RavKav Card Reader - Enhanced Testing Suite" -ForegroundColor Green
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Choose an option:" -ForegroundColor Yellow
    Write-Host "  1 - Check ADB connection"
    Write-Host "  2 - Uninstall old version"
    Write-Host "  3 - Install fresh APK"
    Write-Host "  4 - Launch application"
    Write-Host "  5 - View logcat (real-time)"
    Write-Host "  6 - Extract logcat to file"
    Write-Host "  7 - Clear logcat buffer"
    Write-Host "  8 - Run full test sequence"
    Write-Host "  9 - Exit"
    Write-Host ""
}

function Check-ADB {
    Write-Host ""
    Write-Host "Checking ADB connection..." -ForegroundColor Cyan
    $adbOutput = & adb devices
    Write-Host $adbOutput

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: ADB not found or no devices connected!" -ForegroundColor Red
        Write-Host "Please ensure:" -ForegroundColor Yellow
        Write-Host "  1. Android SDK Platform Tools are installed"
        Write-Host "  2. Device is connected via USB"
        Write-Host "  3. USB Debugging is enabled"
    } else {
        Write-Host "ADB connection OK!" -ForegroundColor Green
    }
}

function Uninstall-App {
    Write-Host ""
    Write-Host "Attempting to uninstall previous version..." -ForegroundColor Cyan
    & adb shell pm uninstall com.unicapitalgroup.ravkavreader

    if ($LASTEXITCODE -eq 0) {
        Write-Host "Uninstall successful!" -ForegroundColor Green
    } else {
        Write-Host "Note: App may not have been installed (this is OK)" -ForegroundColor Yellow
    }
}

function Install-APK {
    Write-Host ""
    Write-Host "Installing RavKav Card Reader Enhanced Edition..." -ForegroundColor Cyan

    $apkFile = "RavKavCardReader_Enhanced_AUTH-2026-001.apk"
    if (-not (Test-Path $apkFile)) {
        Write-Host "ERROR: APK not found in current directory!" -ForegroundColor Red
        Write-Host "Current directory: $(Get-Location)" -ForegroundColor Yellow
        Write-Host "Please ensure the APK is in this directory" -ForegroundColor Yellow
        return $false
    }

    & adb install $apkFile

    if ($LASTEXITCODE -eq 0) {
        Write-Host "Installation successful!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "Installation failed! Check error above." -ForegroundColor Red
        return $false
    }
}

function Launch-App {
    Write-Host ""
    Write-Host "Launching application..." -ForegroundColor Cyan
    & adb shell am start -n com.unicapitalgroup.ravkavreader/.MainActivity

    if ($LASTEXITCODE -eq 0) {
        Write-Host "App launched successfully!" -ForegroundColor Green
        Write-Host "The app should now be running on your device." -ForegroundColor Cyan
    } else {
        Write-Host "Failed to launch app!" -ForegroundColor Red
    }
}

function Show-Logcat-RealTime {
    Write-Host ""
    Write-Host "Displaying real-time logcat (RavKavReaderPoC only)" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop..." -ForegroundColor Yellow
    Write-Host ""
    & adb logcat -s "RavKavReaderPoC" -v threadtime
}

function Extract-Logcat {
    Write-Host ""
    Write-Host "Extracting logcat to file..." -ForegroundColor Cyan

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $logfile = "card_test_results_$timestamp.txt"

    Write-Host "Output file: $logfile" -ForegroundColor Yellow
    & adb logcat -s "RavKavReaderPoC" -d | Out-File -FilePath $logfile -Encoding UTF8

    $fileInfo = Get-Item $logfile
    Write-Host "Logcat saved successfully!" -ForegroundColor Green
    Write-Host "File size: $($fileInfo.Length) bytes" -ForegroundColor Cyan
    Write-Host "Full path: $(Get-Item $logfile | Select-Object -ExpandProperty FullName)" -ForegroundColor Cyan
}

function Clear-Logcat {
    Write-Host ""
    Write-Host "Clearing logcat buffer..." -ForegroundColor Cyan
    & adb logcat -c
    Write-Host "Logcat cleared!" -ForegroundColor Green
}

function Run-FullTest {
    Write-Host ""
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host "FULL TEST SEQUENCE" -ForegroundColor Green
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "This will:" -ForegroundColor Yellow
    Write-Host "  1. Check ADB connection"
    Write-Host "  2. Uninstall old version"
    Write-Host "  3. Install fresh APK"
    Write-Host "  4. Clear logcat"
    Write-Host "  5. Launch application"
    Write-Host ""
    Write-Host "Press Enter to continue or Ctrl+C to cancel..." -ForegroundColor Yellow
    Read-Host

    Write-Host ""
    Write-Host "[1/5] Checking ADB connection..." -ForegroundColor Cyan
    Check-ADB
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: ADB connection failed!" -ForegroundColor Red
        return
    }

    Write-Host ""
    Write-Host "[2/5] Uninstalling old version..." -ForegroundColor Cyan
    Uninstall-App

    Write-Host ""
    Write-Host "[3/5] Installing fresh APK..." -ForegroundColor Cyan
    if (-not (Install-APK)) {
        return
    }

    Write-Host ""
    Write-Host "[4/5] Clearing logcat buffer..." -ForegroundColor Cyan
    Clear-Logcat

    Write-Host ""
    Write-Host "[5/5] Launching application..." -ForegroundColor Cyan
    Launch-App

    Write-Host ""
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host "SETUP COMPLETE!" -ForegroundColor Green
    Write-Host "========================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "  1. App is now running on your device"
    Write-Host "  2. You should see 'Waiting for card...' message"
    Write-Host "  3. Hold card to NFC area to start testing"
    Write-Host "  4. Check logcat in real-time (option 5) or save to file (option 6)"
    Write-Host ""
    Write-Host "When done testing, extract logcat to file for analysis." -ForegroundColor Cyan
    Write-Host ""
}

# Main loop
do {
    Show-Menu
    $choice = Read-Host "Enter option (1-9)"

    switch ($choice) {
        '1' { Check-ADB; Read-Host "Press Enter to continue" }
        '2' { Uninstall-App; Read-Host "Press Enter to continue" }
        '3' { Install-APK; Read-Host "Press Enter to continue" }
        '4' { Launch-App; Read-Host "Press Enter to continue" }
        '5' { Show-Logcat-RealTime }
        '6' { Extract-Logcat; Read-Host "Press Enter to continue" }
        '7' { Clear-Logcat; Read-Host "Press Enter to continue" }
        '8' { Run-FullTest; Read-Host "Press Enter to continue" }
        '9' {
            Write-Host ""
            Write-Host "Goodbye!" -ForegroundColor Green
            exit 0
        }
        default {
            Write-Host "Invalid choice! Please try again." -ForegroundColor Red
            Read-Host "Press Enter to continue"
        }
    }
} while ($true)

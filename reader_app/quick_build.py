#!/usr/bin/env python3
"""
Quick build script for RavKav Card Reader Enhanced
Compiles Java, creates DEX, packages APK
"""

import os
import sys
import subprocess
import shutil
import json
from pathlib import Path
from datetime import datetime

class APKBuilder:
    def __init__(self, script_dir):
        self.script_dir = Path(script_dir)
        self.build_dir = self.script_dir / "build"
        self.classes_dir = self.build_dir / "classes"
        self.dex_dir = self.build_dir / "dex"
        self.dist_dir = self.build_dir / "dist"
        self.src_dir = self.script_dir / "src"

        # Android SDK paths
        self.android_home = os.environ.get("ANDROID_HOME", "C:\\Android\\sdk")
        self.android_jar = f"{self.android_home}\\platforms\\android-34\\android.jar"
        self.build_tools = f"{self.android_home}\\build-tools\\34.0.0"

        self.log = []

    def log_msg(self, msg, level="INFO"):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        log_line = f"[{timestamp}] {level}: {msg}"
        self.log.append(log_line)
        print(log_line)

    def ensure_dirs(self):
        """Create necessary directories"""
        self.log_msg("Creating build directories...")
        for d in [self.classes_dir, self.dex_dir, self.dist_dir]:
            d.mkdir(parents=True, exist_ok=True)

    def compile_java(self):
        """Compile Java sources"""
        self.log_msg("Compiling Java sources...")

        if not self.src_dir.exists():
            self.log_msg(f"Source directory not found: {self.src_dir}", "ERROR")
            return False

        java_files = list(self.src_dir.rglob("*.java"))
        self.log_msg(f"Found {len(java_files)} Java files")

        javac_cmd = ["javac", "-d", str(self.classes_dir), "-source", "1.8", "-target", "1.8"]

        if Path(self.android_jar).exists():
            javac_cmd.extend(["-cp", self.android_jar])
            self.log_msg(f"Using Android classpath: {self.android_jar}")
        else:
            self.log_msg(f"Android jar not found: {self.android_jar}", "WARN")

        javac_cmd.extend([str(f) for f in java_files])

        try:
            result = subprocess.run(javac_cmd, capture_output=True, text=True)
            if result.returncode != 0:
                self.log_msg(f"Compilation error:\n{result.stderr}", "ERROR")
                return False
            self.log_msg("Java compilation successful")
            return True
        except Exception as e:
            self.log_msg(f"Compilation failed: {e}", "ERROR")
            return False

    def create_dex(self):
        """Create DEX file from compiled classes"""
        self.log_msg("Creating DEX file...")

        d8_exe = f"{self.build_tools}\\d8.bat"
        if not Path(d8_exe).exists():
            self.log_msg(f"d8 not found: {d8_exe}", "WARN")
            return False

        class_files = list(self.classes_dir.rglob("*.class"))
        if not class_files:
            self.log_msg("No class files found", "WARN")
            return False

        try:
            cmd = [d8_exe, f"--output={self.dex_dir}"] + [str(f) for f in class_files]
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode != 0:
                self.log_msg(f"DEX creation error:\n{result.stderr}", "WARN")
                return False
            self.log_msg("DEX file created successfully")
            return True
        except Exception as e:
            self.log_msg(f"DEX creation failed: {e}", "WARN")
            return False

    def create_apk(self):
        """Create APK package"""
        self.log_msg("Creating APK package...")

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        apk_name = f"RavKavCardReader_Enhanced_AUTH-2026-001_{timestamp}.apk"
        apk_path = self.dist_dir / apk_name

        # Use Python's zipfile to create APK
        try:
            import zipfile
            with zipfile.ZipFile(apk_path, 'w', zipfile.ZIP_DEFLATED) as zf:
                # Add DEX
                dex_file = self.dex_dir / "classes.dex"
                if dex_file.exists():
                    zf.write(dex_file, arcname="classes.dex")
                    self.log_msg(f"Added: classes.dex ({dex_file.stat().st_size} bytes)")

                # Add manifest
                manifest = self.script_dir / "AndroidManifest.xml"
                if manifest.exists():
                    zf.write(manifest, arcname="AndroidManifest.xml")
                    self.log_msg(f"Added: AndroidManifest.xml ({manifest.stat().st_size} bytes)")

            self.log_msg(f"APK created: {apk_path} ({apk_path.stat().st_size} bytes)")
            return str(apk_path)

        except Exception as e:
            self.log_msg(f"APK creation failed: {e}", "ERROR")
            return None

    def sign_apk(self, apk_path):
        """Sign APK with keystore"""
        self.log_msg("Signing APK...")

        keystore = self.script_dir / "readerapp.keystore"
        if not keystore.exists():
            self.log_msg(f"Keystore not found: {keystore}", "WARN")
            return False

        # Find jarsigner
        java_home = os.environ.get("JAVA_HOME", "")
        if java_home:
            jarsigner = Path(java_home) / "bin" / "jarsigner.exe"
            if not jarsigner.exists():
                jarsigner = shutil.which("jarsigner")
        else:
            jarsigner = shutil.which("jarsigner")

        if not jarsigner:
            self.log_msg("jarsigner not found", "WARN")
            return False

        try:
            cmd = [
                str(jarsigner),
                "-keystore", str(keystore),
                "-storepass", "android",
                "-keypass", "android",
                str(apk_path),
                "androidkey"
            ]
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode != 0:
                self.log_msg(f"Signing error:\n{result.stderr}", "WARN")
                return False
            self.log_msg("APK signed successfully")
            return True
        except Exception as e:
            self.log_msg(f"Signing failed: {e}", "WARN")
            return False

    def build(self):
        """Execute full build process"""
        print("=" * 50)
        print("RavKav Card Reader - Enhanced Build")
        print("AUTH-2026-001")
        print("=" * 50)
        print()

        self.ensure_dirs()

        if not self.compile_java():
            return False

        self.create_dex()  # Optional, ignore failure

        apk_path = self.create_apk()
        if not apk_path:
            return False

        self.sign_apk(apk_path)

        print()
        print("=" * 50)
        print("Build Summary:")
        print(f"  Output APK: {apk_path}")
        print(f"  Build Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"  Version: 1.1-AUTH-2026-001-Enhanced")
        print()
        print("Features:")
        print("  - Probe APDU execution (4 variations)")
        print("  - Real card detection (Empty vs Balance)")
        print("  - Crypto analysis with actual responses")
        print("  - Challenge sequence extraction")
        print("=" * 50)

        # Save build log
        log_file = self.build_dir / "build.log"
        with open(log_file, 'w') as f:
            f.write('\n'.join(self.log))

        return True

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    builder = APKBuilder(script_dir)
    success = builder.build()
    sys.exit(0 if success else 1)

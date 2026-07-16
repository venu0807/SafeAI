#!/bin/bash
# SafeguardAI APK Builder
# Prerequisites: JDK 17+, Android SDK, Android NDK
#
# Usage:
#   export ANDROID_HOME=~/Android/Sdk
#   ./build_apk.sh
#
# Or one-liner:
#   ANDROID_HOME=~/Android/Sdk ./build_apk.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🛡️  SafeguardAI APK Builder"
echo "==========================="
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Install JDK 17:"
    echo "   sudo apt install openjdk-17-jdk"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "✅ Java version: $(java -version 2>&1 | head -1)"

# Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME not set. Install Android SDK and set:"
    echo "   export ANDROID_HOME=~/Android/Sdk"
    echo ""
    echo "   Quick install:"
    echo "   apt install android-sdk"
    echo "   OR download from: https://developer.android.com/studio#command-line-tools-only"
    exit 1
fi
echo "✅ Android SDK: $ANDROID_HOME"

# Build
echo ""
echo "🔨 Building APK..."
./gradlew assembleRelease

echo ""
echo "✅ Done! APK at:"
echo "   $SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
echo ""
echo "📱 Install on device:"
echo "   adb install app/build/outputs/apk/release/app-release.apk"

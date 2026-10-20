#!/usr/bin/env bash
set -e

echo "=== Initializing PuKey Codespaces Environment ==="

# 1. Ensure gradlew is executable
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo "[✓] Executable permissions set for gradlew."
fi

# 2. Ensure local.properties specifies the container's Android SDK path
if [ ! -f "local.properties" ]; then
    echo "sdk.dir=${ANDROID_HOME:-/opt/android-sdk}" > local.properties
    echo "[✓] Generated local.properties pointing to ${ANDROID_HOME:-/opt/android-sdk}."
fi

# 3. Environment diagnostic output
echo "[i] Java Version:"
java -version

echo "[i] Android SDK Platform-Tools:"
adb --version || true

echo "[i] Installed Android SDK Packages:"
sdkmanager --list_installed 2>/dev/null | grep -E "platforms|build-tools|cmake" || true

# 4. Verify Gradle wrapper
echo "[i] Validating Gradle Wrapper..."
./gradlew --version

echo "=== PuKey Environment Ready! ==="
echo "You can now run: ./gradlew assembleDebug or ./gradlew test"


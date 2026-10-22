#!/usr/bin/env bash
set -e

echo "=== Initializing AuthentiKey Codespaces Environment ==="

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

# 3. Setup tailscale-adb helper CLI
if [ -f ".devcontainer/tailscale-adb.sh" ]; then
    chmod +x .devcontainer/tailscale-adb.sh
    sudo ln -sf "$(pwd)/.devcontainer/tailscale-adb.sh" /usr/local/bin/tailscale-adb
    echo "[✓] Installed 'tailscale-adb' CLI to /usr/local/bin/tailscale-adb."
fi

# 4. Ensure /dev/net/tun device exists
if [ ! -c /dev/net/tun ]; then
    sudo mkdir -p /dev/net
    sudo mknod /dev/net/tun c 10 200 || true
    sudo chmod 666 /dev/net/tun || true
fi

# 5. Check and initialize Tailscale daemon
echo "[i] Checking systemd tailscaled service..."
if systemctl is-active --quiet tailscaled; then
    echo "[✓] tailscaled is active and running under systemd."
else
    echo "[i] Starting tailscaled..."
    sudo systemctl start tailscaled || true
fi

# 6. Automatic Tailscale auth if TS_AUTH_KEY is provided
if [ -n "$TS_AUTH_KEY" ]; then
    echo "[*] Authenticating with Tailscale using secret \$TS_AUTH_KEY..."
    sudo tailscale up --authkey="$TS_AUTH_KEY" --accept-routes --hostname="authentikey-codespace" --operator=vscode || true
else
    echo "[i] Tailscale ready. Run 'tailscale-adb up' to authenticate and join your tailnet."
fi

# 7. Environment diagnostic output
echo "[i] Java Version (JDK 21):"
java -version

echo "[i] Android SDK Platform-Tools:"
adb --version || true

echo "[i] Installed Android SDK Packages:"
sdkmanager --list_installed 2>/dev/null | grep -E "platforms|build-tools|cmake" || true

# 8. Verify Gradle wrapper
echo "[i] Validating Gradle Wrapper..."
./gradlew --version

echo "=== AuthentiKey Environment Ready! ==="
echo "Build command:      ./gradlew assembleDebug"
echo "Run tests:          ./gradlew test"
echo "Wireless ADB guide: tailscale-adb help"

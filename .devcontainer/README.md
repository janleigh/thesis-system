# Remote Development with Dev Containers & GitHub Codespaces

This repository includes an enterprise-grade [Dev Container](https://containers.dev) setup optimized for remote development on **GitHub Codespaces** and local development via VS Code **Dev Containers**.

---

## What's Included

The container equips your remote development workspace with:
- **Base OS:** Ubuntu 22.04 LTS (x86_64 and ARM64 architecture support)
- **Init System:** **`systemd`** enabled as PID 1 (`/sbin/init`), allowing background services, daemons, and systemctl units.
- **Java Development Kit:** **OpenJDK 21** (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`)
- **Networking & VPN:** **Tailscale** daemon (`tailscaled`) running as a managed systemd service for zero-config mesh networking and peer-to-peer device communication.
- **Wireless ADB Integration:** Built-in **`tailscale-adb`** CLI tool for connecting Android phones across networks without USB cables.
- **Android SDK:**
  - Command-line Tools (`cmdline-tools/latest`)
  - Target Platform: `platforms;android-34`
  - Build Tools: `build-tools;34.0.0`
  - Platform Tools: `adb`, `fastboot`
  - Native Tools: CMake `3.22.1`, Ninja
- **VS Code Extensions:**
  - Extension Pack for Java (`vscjava.vscode-java-pack`)
  - Kotlin Language Support (`fwcd.kotlin`)
  - Gradle for Java / Android (`vscjava.vscode-gradle`)
  - Red Hat XML (`redhat.vscode-xml`)
  - CMake Tools & C/C++ Intellisense (`ms-vscode.cmake-tools`, `ms-vscode.cpptools`)
  - GitHub Pull Requests & Issues (`GitHub.vscode-pull-request-github`)

---

## Getting Started on GitHub Codespaces

1. Navigate to the GitHub repository page.
2. Click the green **Code** button, select the **Codespaces** tab, and click **Create codespace on master**.
3. GitHub Codespaces will build the container with systemd as PID 1 and run the initialization script (`post-create.sh`).
4. Once the terminal opens, your environment is ready to build and debug.

---

## Build Commands

From the integrated terminal in Codespaces or VS Code:

### 1. Compile and Assemble Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Unit Tests
```bash
./gradlew test
# Or specifically unit tests for debug build:
./gradlew testDebugUnitTest
```

---

## Tailscale & Wireless ADB Debugging

Because cloud-hosted containers lack physical USB ports, this devcontainer integrates **Tailscale** and **systemd** to enable frictionless **Wireless ADB debugging** directly between your Codespace and your physical Android device.

### Step 1: Connect your Phone to Tailscale
1. Install and log in to the **Tailscale** app on your Android phone.
2. Note your phone's Tailscale IP address (e.g., `100.x.y.z`) from the Tailscale app.

### Step 2: Enable Wireless Debugging on Phone
* **Option A (Android 11+ Wireless Debugging):**
  - Go to **Settings > System > Developer Options > Wireless Debugging**.
  - Enable it and note the port (e.g. `100.x.y.z:5555` or dynamic pairing port).
* **Option B (Standard TCP/IP ADB on port 5555):**
  - Plug phone into any computer once after reboot and run:
    ```bash
    adb tcpip 5555
    ```

### Step 3: Connect from Codespaces
Inside your Codespaces terminal, use the pre-installed **`tailscale-adb`** utility:

1. **Join Tailscale:**
   ```bash
   tailscale-adb up
   ```
   *(If you configured a `TS_AUTH_KEY` secret in your repository's Codespaces settings, this authentication happens automatically upon container creation!)*

2. **Connect to your phone:**
   ```bash
   tailscale-adb connect <phone-tailscale-ip>
   # Example:
   tailscale-adb connect 100.85.20.14
   ```

3. **Authorize on device:**
   Look at your phone screen and tap **"Always allow from this computer"**.

4. **Verify connection:**
   ```bash
   tailscale-adb devices
   ```
   You will see your phone connected (e.g., `100.85.20.14:5555 device`).

5. **Deploy & Debug directly from Codespaces:**
   ```bash
   ./gradlew installDebug
   ```
   The APK installs and launches straight from GitHub Codespaces over Tailscale!

---

## `tailscale-adb` Command Reference

| Command | Description |
|---|---|
| `tailscale-adb up` | Authenticate and join your Tailscale tailnet |
| `tailscale-adb connect <ip> [port]` | Connect Wireless ADB to an Android device (default port: 5555) |
| `tailscale-adb disconnect <ip> [port]` | Disconnect ADB session |
| `tailscale-adb status` | Show status of Tailscale daemon, systemd unit, and ADB targets |
| `tailscale-adb devices` | List active Tailscale network peers and ADB devices |
| `tailscale-adb help` | Display command usage and quick troubleshooting workflow |

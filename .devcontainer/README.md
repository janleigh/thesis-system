# Remote Development with Dev Containers & GitHub Codespaces

This repository includes a fully configured [Dev Container](https://containers.dev) setup optimized for remote development on **GitHub Codespaces** and local development via VS Code **Dev Containers**.

---

## What's Included

The dev container automatically equips your environment with:
- **Base OS:** Ubuntu 22.04 LTS (x86_64 and ARM64 architecture support)
- **Java Development Kit:** OpenJDK 17 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk`)
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
3. GitHub Codespaces will build the Docker container and run the automated initialization script (`post-create.sh`), preparing the Android SDK and Gradle wrapper.
4. Once the terminal opens, your environment is ready to build!

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
You can right-click the APK in the VS Code file explorer and choose **Download...** to transfer it directly to your local computer or phone.

### 2. Run Unit Tests
```bash
./gradlew test
# Or specifically unit tests for debug build:
./gradlew testDebugUnitTest
```

### 3. Check Dependencies & Tasks
```bash
./gradlew tasks
./gradlew androidDependencies
```

---

## Connecting Physical Android Devices

Since cloud containers do not run graphical hardware-accelerated emulators directly, testing on Android can be performed via:

1. **Direct APK Installation:** Download the compiled `app-debug.apk` and sideload it onto your test device.
2. **Wireless ADB over Network / VPN:**
   - Enable **Developer Options** and **Wireless Debugging** on your Android phone.
   - Connect via ADB inside the Codespace terminal:
     ```bash
     adb connect <device-ip>:<pairing-port>
     ```
   - (For secure cross-network bridging between your local phone and Codespaces, tools like [Tailscale](https://tailscale.com) or [WebADB](https://webadb.com) can be used).


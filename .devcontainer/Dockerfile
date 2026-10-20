FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Avoid prompts during apt installations
ENV DEBIAN_FRONTEND=noninteractive

# 1. Install systemd, networking utilities, OpenJDK 21, and core Android build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    systemd \
    systemd-sysv \
    udev \
    dbus \
    iptables \
    iproute2 \
    openjdk-21-jdk-headless \
    git \
    curl \
    wget \
    unzip \
    zip \
    build-essential \
    cmake \
    ninja-build \
    libglu1-mesa \
    libpulse0 \
    ca-certificates \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# 2. Configure systemd for container environment (clean up non-container targets)
RUN rm -f /lib/systemd/system/multi-user.target.wants/getty.target \
    /lib/systemd/system/systemd-remount-fs.service \
    /lib/systemd/system/sys-kernel-config.mount \
    /lib/systemd/system/systemd-udevd.service

# 3. Dynamic multi-architecture JAVA_HOME configuration (JDK 21)
RUN ARCH=$(dpkg --print-architecture) && \
    ln -s /usr/lib/jvm/java-21-openjdk-${ARCH} /usr/lib/jvm/java-21-openjdk

ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# 4. Android SDK installation & configuration (API 34, build-tools 34.0.0, cmake 3.22.1)
ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="/opt/android-sdk"
ENV CMDLINE_TOOLS_VERSION="11076708"
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0"

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "cmake;3.22.1"

# 5. Install Tailscale and enable systemd tailscaled service
RUN curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/jammy.noarmor.gpg | tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null && \
    curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/jammy.tailscale-repo.list | tee /etc/apt/sources.list.d/tailscale.list && \
    apt-get update && apt-get install -y --no-install-recommends tailscale && \
    rm -rf /var/lib/apt/lists/* && \
    systemctl enable tailscaled

# 6. Global environment persistence and user permissions
RUN echo "export JAVA_HOME=${JAVA_HOME}" >> /etc/profile.d/android.sh && \
    echo "export ANDROID_HOME=${ANDROID_HOME}" >> /etc/profile.d/android.sh && \
    echo "export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}" >> /etc/profile.d/android.sh && \
    echo "export PATH=\${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools" >> /etc/profile.d/android.sh && \
    chmod +x /etc/profile.d/android.sh && \
    mkdir -p /home/vscode/.gradle /var/lib/tailscale && \
    chown -R vscode:vscode ${ANDROID_HOME} /home/vscode/.gradle

# 7. Start container with systemd as PID 1
ENTRYPOINT ["/sbin/init"]

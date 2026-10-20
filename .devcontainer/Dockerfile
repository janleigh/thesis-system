FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Avoid prompts during apt installations
ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK 17, C++ build tools, and essential utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
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
    && rm -rf /var/lib/apt/lists/*

# Architecture-independent JAVA_HOME link
RUN ARCH=$(dpkg --print-architecture) && \
    ln -s /usr/lib/jvm/java-17-openjdk-${ARCH} /usr/lib/jvm/java-17-openjdk

ENV JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Android SDK configuration
ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="/opt/android-sdk"
ENV CMDLINE_TOOLS_VERSION="11076708"
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0"

# Download and install Android Command-line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept SDK licenses and install required platforms and build tools
RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "cmake;3.22.1"

# Prepare environment persistence and user permissions
RUN echo "export JAVA_HOME=${JAVA_HOME}" >> /etc/profile.d/android.sh && \
    echo "export ANDROID_HOME=${ANDROID_HOME}" >> /etc/profile.d/android.sh && \
    echo "export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}" >> /etc/profile.d/android.sh && \
    echo "export PATH=\${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools" >> /etc/profile.d/android.sh && \
    chmod +x /etc/profile.d/android.sh && \
    mkdir -p /home/vscode/.gradle && \
    chown -R vscode:vscode ${ANDROID_HOME} /home/vscode/.gradle

USER vscode


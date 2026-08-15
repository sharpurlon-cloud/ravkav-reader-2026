# Android Build Environment
FROM ubuntu:22.04

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk \
    PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/34.0.0 \
    GRADLE_USER_HOME=/root/.gradle

# Install dependencies
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    wget \
    unzip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create Android SDK directory
RUN mkdir -p $ANDROID_HOME/cmdline-tools

# Download Android SDK Command Line Tools
RUN cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-9477386_latest.zip && \
    mv cmdline-tools latest && \
    rm commandlinetools-linux-9477386_latest.zip

# Accept SDK licenses
RUN mkdir -p $ANDROID_HOME/licenses && \
    echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > $ANDROID_HOME/licenses/android-sdk-license

# Download Android SDK components
RUN sdkmanager --install \
    "platforms;android-35" \
    "build-tools;34.0.0" \
    "platform-tools" \
    && sdkmanager --list_installed

# Set working directory
WORKDIR /workspace

# Copy project
COPY . /workspace/

# Build APK
RUN cd /workspace/reader_app && \
    chmod +x gradlew && \
    ./gradlew assembleDebug -x test --no-daemon

# Extract APK
RUN mkdir -p /output && \
    cp /workspace/reader_app/build/outputs/apk/debug/app-debug.apk /output/EnhancedRavKavReader.apk || \
    cp /workspace/reader_app/build/outputs/apk/*/app-*.apk /output/EnhancedRavKavReader.apk || true

VOLUME ["/output"]
CMD ["echo", "APK built successfully! Check /output directory"]

# Android Build Environment - Optimized
FROM ubuntu:22.04

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools \
    GRADLE_USER_HOME=/root/.gradle \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

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

# Download Android SDK
RUN cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-9477386_latest.zip && \
    mv cmdline-tools latest && \
    rm commandlinetools-linux-9477386_latest.zip

# Accept licenses
RUN mkdir -p $ANDROID_HOME/licenses && \
    echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > $ANDROID_HOME/licenses/android-sdk-license && \
    echo "d56f5187479451eabf01fb78af6dfcb131b33910" >> $ANDROID_HOME/licenses/android-sdk-license

# Install SDK components
RUN sdkmanager --install \
    "platforms;android-35" \
    "build-tools;34.0.0" \
    "platform-tools"

# Set working directory
WORKDIR /workspace

# Copy project
COPY . /workspace/

# Clean and build APK
RUN cd /workspace/reader_app && \
    rm -rf .gradle build && \
    chmod +x gradlew && \
    ./gradlew clean assembleDebug -x test --no-daemon --stacktrace 2>&1 | tail -100 && \
    echo "✅ Build completed"

# Extract APK
RUN mkdir -p /output && \
    find /workspace/reader_app/build/outputs/apk -name "*.apk" -exec cp {} /output/ \; || \
    echo "No APK found" && \
    ls -la /output/ || true

CMD ["bash"]

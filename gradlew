#!/bin/sh
# Gradle wrapper script - downloads and runs Gradle

GRADLE_VERSION=8.2
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_BIN="$GRADLE_HOME/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -f "$GRADLE_BIN" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_HOME"
    wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d "$GRADLE_HOME"
    rm /tmp/gradle.zip
fi

exec "$GRADLE_BIN" "$@"

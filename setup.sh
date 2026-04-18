#!/bin/bash
# Set up empty Android project
mkdir -p app/src/main/java/com/stegovault
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/values-fr
mkdir -p app/src/main/res/navigation
mkdir -p app/src/main/res/menu
mkdir -p app/src/main/res/xml
mkdir -p app/src/androidTest/java/com/stegovault
mkdir -p app/src/test/java/com/stegovault
mkdir -p gradle/wrapper
mkdir -p .github/workflows

# Create basic files
touch build.gradle.kts
touch settings.gradle.kts
touch gradle.properties
touch app/build.gradle.kts
touch app/src/main/AndroidManifest.xml

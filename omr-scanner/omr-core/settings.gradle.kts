// Standalone build for the OMR engine so it can be tested on any JVM without the Android SDK.
// The Android app compiles these same sources directly (see app/build.gradle.kts).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "omr-core"

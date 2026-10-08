import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.omrscanner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omrscanner.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A fixed key checked into the repo, so every build installs over the previous one.
        // Use your own private key instead before publishing to Google Play.
        create("shared") {
            storeFile = file("signing.p12")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "omr"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    // The OMR engine lives in ../omr-core (a plain Kotlin library with its own JVM tests).
    sourceSets["main"].java.srcDir("../omr-core/src/main/kotlin")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

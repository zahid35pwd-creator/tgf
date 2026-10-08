import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
    // Set -PsheetOut=<dir> to also write the sample images and printable sheets used by the tests.
    project.findProperty("sheetOut")?.let { systemProperty("sheetOut", it.toString()) }
    testLogging { events("passed", "failed"); showStandardStreams = true }
}

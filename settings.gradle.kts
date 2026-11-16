rootProject.name = "warlock3"

include(":core")
include(":wrayth")
include(":telnet")
include(":compose")
include(":scripting")

// Apps
include(":desktopApp")
include(":androidApp")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Only for com.jetbrains.intellij.platform:icons (the SVGs behind Jewel's
        // AllIconsKeys), which isn't on Maven Central. This repository serves IntelliJ
        // build output, not curated releases, so nothing else may resolve from it.
        maven("https://www.jetbrains.com/intellij-repository/releases") {
            content { includeModule("com.jetbrains.intellij.platform", "icons") }
        }
        // mavenLocal()
    }
}

plugins {
    // See https://splitties.github.io/refreshVersions/
    id("de.fayard.refreshVersions") version "0.60.6"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

refreshVersions {
    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
}

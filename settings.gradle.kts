rootProject.name = "warlock3"

include(":core")
include(":wrayth")
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
        maven("https://maven.hq.hydraulic.software")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://www.jetbrains.com/intellij-repository/releases")
        //maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        mavenLocal()
    }
}

plugins {
    // See https://splitties.github.io/refreshVersions/
    id("de.fayard.refreshVersions") version "0.60.6"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

refreshVersions {
    // work-around https://github.com/Splitties/refreshVersions/issues/640
    file("build/tmp/refreshVersions").mkdirs()
    versionsPropertiesFile = file("build/tmp/refreshVersions/versions.properties")

    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
}

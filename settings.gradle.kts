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
        // mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // This repository serves IntelliJ monorepo build output rather than curated
        // releases, so treat it as a narrow escape hatch: it is only here because
        // com.jetbrains.intellij.platform (we need :icons, a resource-only SVG jar for
        // Jewel's AllIconsKeys lookups) is not published to Maven Central at all.
        //
        // The content filter keeps anything else from silently resolving from here.
        // That matters because artifacts published only to this repo carry real hazards:
        // incomplete POMs, and versions whose API changes without the version changing.
        // v3.1.0-beta.21 shipped a startup crash that started exactly this way, with
        // Jewel drifting from a Maven Central release onto an IJ-repo-only 0.38 build.
        // With this filter that bump fails at dependency resolution instead of at launch.
        maven("https://www.jetbrains.com/intellij-repository/releases") {
            content { includeGroupByRegex("com\\.jetbrains\\.intellij\\..*") }
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

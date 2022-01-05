rootProject.name = "warlock3"
include(":core")
include(":stormfront")
include(":app")

plugins {
    // See https://jmfayard.github.io/refreshVersions
    id("de.fayard.refreshVersions") version "0.30.1"
}

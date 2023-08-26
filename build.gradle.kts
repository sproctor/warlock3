plugins{
    kotlin("jvm") apply false
    id("org.jetbrains.compose") apply false
    kotlin("plugin.serialization") apply false
    id("app.cash.sqldelight") apply false
}

subprojects {
    version = "3.0.8"
    group = "cc.warlock.warlock3"
}

tasks.wrapper {
    gradleVersion = "8.3"
}
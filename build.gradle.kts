plugins{
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    version = "3.0.11"
    group = "cc.warlock.warlock3"
}

tasks.wrapper {
    gradleVersion = "8.4"
}

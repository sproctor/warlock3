plugins{
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    version = project.findProperty("warlock.version")!!.toString()
    group = "cc.warlock.warlock3"
}

tasks.wrapper {
    gradleVersion = "8.5"
}

import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room.schema) apply false
    alias(libs.plugins.antlr.kotlin) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "warlockfe.warlock3"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.asProvider().get())
        filter {
            exclude { entry ->
                entry.file.invariantSeparatorsPath.contains("/build/")
            }
        }
    }

    dependencies {
        add("ktlintRuleset", rootProject.libs.ktlint.compose.rules)
    }
}

val Project.libs: org.gradle.accessors.dm.LibrariesForLibs
    get() = extensions.getByType()

tasks.wrapper {
    gradleVersion = "9.5.0"
}

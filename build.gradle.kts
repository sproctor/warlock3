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

subprojects {
    group = "warlockfe.warlock3"

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

// CI passes -PlintSkip=true to drop Android Lint from the `check` graph — its model
// generation + analyze tasks cost ~50s and most of what it catches in this Kotlin-only
// Compose project is already covered by ktlint or the Kotlin compiler.
if ((findProperty("lintSkip") as? String)?.toBoolean() == true) {
    allprojects {
        afterEvaluate {
            tasks
                .matching { it.name.startsWith("lint") || it.name.contains("Lint") }
                .configureEach { enabled = false }
        }
    }
}

// Android `check` runs both Debug and Release unit-test variants; they execute the same
// JVM tests twice. Drop the Release variant — release unit-test coverage is exercised
// at release time by the dedicated release workflow.
allprojects {
    afterEvaluate {
        tasks
            .matching {
                it.name == "testReleaseUnitTest" ||
                    it.name == "compileReleaseUnitTestKotlinAndroid" ||
                    it.name == "processReleaseUnitTestJavaRes"
            }.configureEach { enabled = false }
    }
}

tasks.wrapper {
    gradleVersion = "9.5.0"
}

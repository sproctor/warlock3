import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room.schema) apply false
    alias(libs.plugins.antlr.kotlin) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    group = "warlockfe.warlock3"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension> {
        version = "1.8.0"
        filter {
            exclude { entry ->
                entry.file.invariantSeparatorsPath.contains("/build/")
            }
        }
    }

    dependencies {
        add("ktlintRuleset", rootProject.libs.ktlint.compose.rules)

        // Jewel's IntelliJ icons artifacts pull in JetBrains' fork of coroutines instead of
        // the stock library. The fork can be out of sync with upstream, and since it has a
        // different Maven group, Gradle would ship both. Use the stock artifact.
        // https://youtrack.jetbrains.com/issue/JEWEL-1384
        modules {
            module("org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm") {
                replacedBy(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm",
                    "Duplicate coroutines on the classpath; keep the stock artifact",
                )
            }
        }
    }
}

val Project.libs: org.gradle.accessors.dm.LibrariesForLibs
    get() = extensions.getByType()

// -PlintSkip=true drops Android Lint from the `check` graph — its model generation + analyze
// tasks cost ~50s — for builds that do not need it, like the release bundle. CI keeps lint in
// `check`: it is the only check that knows which Java APIs Android lacks. Path.of shipped in
// v3.1.0 and crashed every device before Android 14 while ktlint, the compiler and every
// test stayed green.
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

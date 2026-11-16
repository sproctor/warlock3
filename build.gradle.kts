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

        // The IntelliJ platform icons artifacts (icons-api, which Jewel pulls in) depend on
        // JetBrains' fork of coroutines, which sits under a different Maven group and so
        // never conflict-resolves against the stock kotlinx-coroutines-core we ask for.
        // Both jars carry kotlinx/coroutines/*, so without this rule both land in the
        // packaged app and classpath order decides which implementation it runs. The fork
        // has drifted from upstream before: at 1.10.2 it lacked BuildersKt.runBlockingK,
        // which Kotlin 2.4 compiles `runBlocking` down to, and a fork-first classpath
        // crashed at startup with NoSuchMethodError. It now tracks 1.11.0
        // (1.11.0-intellij-1), but nothing guarantees it keeps up. Collapse the two onto
        // the stock artifact.
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

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

// JMH (which kotlinx-benchmark uses on the JVM) subclasses @State benchmark classes, so they must be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
    annotation("kotlinx.benchmark.State")
}

val skipIos = (findProperty("iosSkip") as? String)?.toBoolean() == true

kotlin {
    jvm {
        // Separate compilation so benchmark code + the kotlinx-benchmark runtime stay out of the
        // published library. Sources live in src/jvmBenchmark/kotlin; it can see the main classpath.
        val main by compilations.getting
        compilations.create("benchmark") {
            associateWith(main)
        }
    }
    androidLibrary {
        namespace = "warlockfe.warlock3.compose"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        androidResources.enable = true
    }
    if (!skipIos) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "Compose"
                isStatic = true
            }
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("commonJvmAndroid") {
                withJvm()
                withAndroidTarget()
                // Following line can be removed when https://issuetracker.google.com/issues/442950553 is fixed
                withCompilations { it is KotlinMultiplatformAndroidCompilation }
            }
            group("mobile") {
                withAndroidTarget()
                // Following line can be removed when https://issuetracker.google.com/issues/442950553 is fixed
                withCompilations { it is KotlinMultiplatformAndroidCompilation }
                // Keep the ios subgroup declared even when iOS targets are skipped so
                // the mobileMain source set is still created for androidMain.
                group("ios") {
                    withIos()
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":wrayth")) // TODO: remove when abstracting DI

            implementation(libs.kotlinx.serialization.json)

            // Compose
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.tooling.preview)

            // Third party UI
            implementation(libs.colorpicker)
            implementation(libs.filekit.dialogs)

            // Other stuff
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqlite.bundled)
            implementation(libs.coil.compose)
            implementation(libs.room.runtime)
            implementation(libs.reorderable)
        }
        getByName(if (skipIos) "androidMain" else "mobileMain") {
            dependencies {
                implementation(libs.compose.material3)
                implementation(libs.fastscroller.core)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("jvmBenchmark").dependencies {
            implementation(libs.kotlinx.benchmark.runtime)
            // The host's Skia/desktop runtime, so TextMeasurer-based text-layout benchmarks can run
            // (the library compilation doesn't bundle the skiko native lib).
            implementation(compose.desktop.currentOs)
        }

        getByName("commonJvmAndroidMain") {
            dependencies {
                implementation(libs.coil.network.okhttp)
                implementation(libs.autolink)
            }
        }
        jvmMain.dependencies {
            implementation(libs.jewel.standalone)
            // Jewel references AllIconsKeys (e.g. ComboBox chevron) but its standalone POM
            // does not pull the platform icons jar, so the SVG resources have to be added
            // explicitly or every IntelliJ-icon-keyed Icon renders as a magenta placeholder.
            implementation(libs.intellij.platform.icons)
        }
        if (!skipIos) {
            iosMain.dependencies {
                implementation(project(":scripting"))
                // Pure-Kotlin DEFLATE for the iOS in-memory zip reader (see ZipReader.ios.kt).
                implementation(libs.kompress.core)
            }
        }
    }

    jvmToolchain(
        libs.versions.jvmToolchainVersion
            .get()
            .toInt(),
    )

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    // For previews (new AGP KMP library plugin does not create per-build-type source sets)
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

benchmark {
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
        }
    }
    targets {
        register("jvmBenchmark")
    }
}

// The default skin is authored as a directory (skin.json + referenced images) under skin/ and
// packaged into a zip at build time, instead of committing a binary zip. Reproducible so it doesn't
// churn between builds. The zip readers on every platform (java.util.zip on JVM/Android, kompress on
// iOS) handle DEFLATE, so the archive is compressed.
val packageDefaultSkin by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir("skin"))
    archiveFileName.set("skin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/skin/files"))
    entryCompression = ZipEntryCompression.DEFLATED
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

compose {
    resources {
        publicResClass = true
        // Attach the generated skin directory to each platform leaf source set rather than
        // commonMain: a custom directory replaces a source set's convention directory, and the
        // drawables/fonts live in commonMain's convention directory.
        val skinResourceDir = packageDefaultSkin.map { layout.buildDirectory.dir("generated/skin").get() }
        customDirectory(sourceSetName = "jvmMain", directoryProvider = skinResourceDir)
        customDirectory(sourceSetName = "androidMain", directoryProvider = skinResourceDir)
        if (!skipIos) {
            customDirectory(sourceSetName = "iosMain", directoryProvider = skinResourceDir)
        }
    }
}

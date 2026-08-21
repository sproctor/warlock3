@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
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
    android {
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
                @Suppress("UnstableApiUsage")
                withAndroid()
            }
            group("mobile") {
                @Suppress("UnstableApiUsage")
                withAndroid()
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
            // The concrete client + scripting implementations that AppContainer wires up.
            // TODO: remove when abstracting DI
            implementation(project(":wrayth"))
            implementation(project(":scripting"))

            implementation(libs.kotlinx.serialization.json)

            // Compose
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.tooling.preview)

            // Navigation 3 (multiplatform: shared between desktop and mobile)
            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)

            // Third party UI
            implementation(libs.colorpicker)
            implementation(libs.filekit.dialogs)
            // The docking layout engine; the renderers live in the platform source sets
            // (Jewel on desktop, Material 3 on mobile).
            implementation(libs.compose.docking.core)

            // Other stuff
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqlite.bundled)
            implementation(libs.coil.compose)
            implementation(libs.room.runtime)
            implementation(libs.reorderable)
            // DEFLATE for the shared in-memory zip reader (see ZipReader.kt). Okio's Inflater is
            // java.util.zip on JVM/Android and platform.zlib on iOS, so both get native zlib from
            // one common call. Note its zlib APIs are JVM+Native only: adding a js/wasm target
            // would make them invisible to commonMain.
            implementation(libs.okio)
        }
        getByName(if (skipIos) "androidMain" else "mobileMain") {
            dependencies {
                implementation(libs.compose.material3)
                implementation(libs.compose.docking.material3)
                implementation(libs.fastscroller.core)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            // The host's Skia/desktop runtime, for the tests that drive a real ImageComposeScene
            // (the library compilation doesn't bundle the skiko native lib).
            implementation(compose.desktop.currentOs)
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

                // Crash reporting for desktop and Android. Deliberately NOT in commonMain: the
                // Kotlin SDK's iOS cinterop carries `-framework Sentry`, which Xcode satisfies via
                // SPM but Gradle cannot when it links the iOS test binary, breaking `check`. iOS
                // starts the Cocoa SDK directly from iOSApp.swift instead.
                implementation(libs.sentry.kotlin)
            }
        }
        jvmMain.dependencies {
            implementation(libs.jewel.standalone)
            implementation(libs.compose.docking.jewel)
            // Title-bar + decorated-window styling for WarlockDesktopTheme (Jewel's DecoratedWindow).
            implementation(libs.jewel.decorated.window)
            // Jewel references AllIconsKeys (e.g. ComboBox chevron) but its standalone POM
            // does not pull the platform icons jar, so the SVG resources have to be added
            // explicitly or every IntelliJ-icon-keyed Icon renders as a magenta placeholder.
            implementation(libs.intellij.platform.icons)
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

// End-to-end multi-connection stream lag harness (StreamNetworkBenchmark.kt). Not a JMH benchmark:
// it stands up a loopback replay server and drives real WraythClients through the full parse+render
// pipeline, so it runs as a plain main() on the jvmBenchmark compilation's classpath. Knobs are passed
// as -P properties; -PgcLog/-Pzgc mirror the desktop app's diagnostic flags.
//   ./gradlew :compose:streamNetworkBenchmark -Pconnections=4 -Pwindows=6 -PlinesPerSec=4000 -PgcLog
val streamBenchmarkCompilation = kotlin.jvm().compilations.getByName("benchmark")
tasks.register<JavaExec>("streamNetworkBenchmark") {
    group = "benchmark"
    description = "Run the end-to-end multi-connection stream lag harness over a loopback socket."
    dependsOn(streamBenchmarkCompilation.compileTaskProvider)
    classpath(
        streamBenchmarkCompilation.output.allOutputs,
        streamBenchmarkCompilation.runtimeDependencyFiles,
    )
    mainClass.set("warlockfe.warlock3.compose.util.StreamNetworkBenchmarkKt")
    for (knob in listOf("connections", "windows", "lines", "highlights", "linesPerSec", "scripts", "uiCostMicros", "log")) {
        (project.findProperty(knob) as? String)?.let { systemProperty(knob, it) }
    }
    if (project.hasProperty("gcLog")) {
        val gcLogPath =
            layout.buildDirectory
                .get()
                .asFile
                .resolve("gc-bench-%p.log")
        jvmArgs("-Xlog:gc*,safepoint:file=$gcLogPath:time,uptime,level,tags:filecount=5,filesize=20M")
    }
    if (project.hasProperty("zgc")) {
        jvmArgs("-XX:+UseZGC")
    }
}

// The default skin is authored as a directory (skin.json + referenced images) under skin/ and
// packaged into a zip at build time, instead of committing a binary zip. Reproducible so it doesn't
// churn between builds. The shared zip reader handles DEFLATE, so the archive is compressed.
val packageDefaultSkin by tasks.registering(Zip::class) {
    description = "Creates skin zip from skin files"
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

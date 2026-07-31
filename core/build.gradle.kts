@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room.schema)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

/** The LWJGL natives classifier for the machine running the build. */
val lwjglNatives: String =
    run {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").lowercase().let { it.startsWith("aarch64") || it.startsWith("arm") }
        when {
            os.contains("win") -> "natives-windows"
            os.contains("mac") || os.contains("darwin") -> if (arm) "natives-macos-arm64" else "natives-macos"
            else -> if (arm) "natives-linux-arm64" else "natives-linux"
        }
    }

val generateKotlinGrammarSource =
    tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {

        dependsOn("cleanGenerateKotlinGrammarSource")

        // ANTLR .g4 files are under {example-project}/antlr
        // Only include *.g4 files. This allows tools (e.g., IDE plugins)
        // to generate temporary files inside the base path
        source =
            fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
                include("**/*.g4")
            }

        // We want the generated source files to have this package name
        val pkgName = "warlockfe.warlock3.core.parsers.generated"
        packageName = pkgName

        // We want visitors alongside listeners.
        // The Kotlin target language is implicit, as is the file encoding (UTF-8)
        arguments = listOf("-visitor")

        // Generated files are outputted inside build/generatedAntlr/{package-name}
        val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
        outputDirectory =
            layout.buildDirectory
                .dir(outDir)
                .get()
                .asFile
    }

// This may or may not be needed
project.tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn.add(generateKotlinGrammarSource)
}

val skipIos = (findProperty("iosSkip") as? String)?.toBoolean() == true

kotlin {
    jvm()
    android {
        namespace = "warlockfe.warlock3.core"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }
    if (!skipIos) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "core"
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
        }
    }

    sourceSets {
        commonMain {
            kotlin {
                srcDir(generateKotlinGrammarSource)
            }
            dependencies {
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.collections.immutable)
                api(libs.kermit)

                // Preferences
                api(libs.room.runtime)
                api(libs.kotlinx.serialization.core)
                // NOTE: api (not implementation) on purpose - IntelliJ/AS KMP support doesn't always
                // put implementation-scoped commonMain deps on the editor analysis classpath, which
                // shows tomlkt as "unresolved" in commonMain files even though Gradle compiles fine.
                api(libs.tomlkt)

                // Networking libs
                api(libs.uri)
                api(libs.ktor.utils)
                // MUD Mobile integration: REST client over HTTPS
                api(libs.ktor.client.core)
                api(libs.ktor.client.cio)
                api(libs.kotlinx.serialization.json)

                implementation(libs.antlr.kotlin.runtime)
            }
        }
        jvmMain {
            dependencies {
                // Desktop audio output (DesktopSoundPlayer); the matching natives are added below.
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.openal)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
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
    // LWJGL ships its native libraries as classifier artifacts, one per platform. The release
    // workflow builds each platform on its own runner, so the build host's natives are the ones
    // that belong in the package. Declared here because variantOf() is not available inside the
    // multiplatform source set blocks.
    add("jvmMainRuntimeOnly", variantOf(libs.lwjgl.core) { classifier(lwjglNatives) })
    add("jvmMainRuntimeOnly", variantOf(libs.lwjgl.openal) { classifier(lwjglNatives) })

    // Room
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    if (!skipIos) {
        add("kspIosSimulatorArm64", libs.room.compiler)
        add("kspIosArm64", libs.room.compiler)
    }
}

room3 {
    schemaDirectory("$projectDir/room")
}

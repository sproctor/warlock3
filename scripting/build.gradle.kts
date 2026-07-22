@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.antlr.kotlin)
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
        val pkgName = "warlockfe.warlock3.scripting.parsers.generated"
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

val skipIos = (findProperty("iosSkip") as? String)?.toBoolean() == true

kotlin {
    android {
        namespace = "warlockfe.warlock3.scripting"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }
    jvm()
    if (!skipIos) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "scripting"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin {
                srcDir(generateKotlinGrammarSource)
            }
            dependencies {
                implementation(project(":core"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)

                // Parsing
                implementation(libs.antlr.kotlin.runtime)

                // Lua scripting engine
                implementation(libs.lua.kmp)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
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
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

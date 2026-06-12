@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

// JMH (which kotlinx-benchmark uses on the JVM) subclasses @State benchmark classes, so they must be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
    annotation("kotlinx.benchmark.State")
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
        val pkgName = "warlockfe.warlock3.wrayth.parsers.generated"
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
    jvm {
        // A separate compilation so benchmark code (and the kotlinx-benchmark runtime) stays out of the
        // published library. Sources live in src/jvmBenchmark/kotlin; it can see the main classpath.
        val main by compilations.getting
        compilations.create("benchmark") {
            associateWith(main)
        }
    }
    androidLibrary {
        namespace = "warlockfe.warlock3.wrayth"
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
                baseName = "wrayth"
                isStatic = true
            }
        }
    }

    jvmToolchain(
        libs.versions.jvmToolchainVersion
            .get()
            .toInt(),
    )

    applyDefaultHierarchyTemplate {
        common {
            group("commonJvmAndroid") {
                withJvm()
                withAndroidTarget()
                // Following line can be removed when https://issuetracker.google.com/issues/442950553 is fixed
                withCompilations { it is KotlinMultiplatformAndroidCompilation }
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
                implementation(project(":scripting"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.xmlutil.serialization)
                api(libs.antlr.kotlin.runtime)
                implementation(libs.ktor.network)
                implementation(libs.ktor.network.tls)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("jvmBenchmark").dependencies {
            implementation(libs.kotlinx.benchmark.runtime)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
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
        // <jvm target><Benchmark compilation> -> "jvmBenchmark"
        register("jvmBenchmark")
    }
}

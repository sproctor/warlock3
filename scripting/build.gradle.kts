@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.antlr.kotlin)
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
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
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

kotlin {

    androidTarget()
    jvm()
//    androidLibrary {
//        namespace = "warlockfe.warlock3.scripting"
//        compileSdk = libs.versions.compileSdk.get().toInt()
//        minSdk = libs.versions.minSdk.get().toInt()
//    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "scripting"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("commonJvmAndroid") {
                withJvm()
                withAndroidTarget()
                // The following is for when we move the android kmp
                // Following line can be remove when https://issuetracker.google.com/issues/442950553 is fixed
                withCompilations { it is KotlinMultiplatformAndroidCompilation } // this class is provided by `com.android.kotlin.multiplatform.library`
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
                implementation(libs.ktor.utils)

                // Needed for JS scripting implementation
                implementation(libs.kotlin.reflect)

                // BigDecimal
                implementation(libs.bignum)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val commonJvmAndroidMain by getting {
            dependencies {
                // TODO: make sure this works on android
                implementation(libs.rhino)
            }
        }
    }
    jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "warlockfe.warlock3.scripting"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

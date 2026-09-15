@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.android.lint)
}

val skipIos = (findProperty("iosSkip") as? String)?.toBoolean() == true

kotlin {
    jvm()
    android {
        namespace = "warlockfe.warlock3.telnet"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        // Run commonTest against the Android target too, on the host JVM (testAndroidHostTest).
        withHostTest {}
    }

    if (!skipIos) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "telnet"
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
                @Suppress("UnstableApiUsage")
                withAndroid()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            // Only for the plain/TLS socket helpers (openPlainSocket / openTLSSocket), which carry
            // the per-platform TLS implementations. The Wrayth protocol itself is not used here.
            implementation(project(":wrayth"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.network)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

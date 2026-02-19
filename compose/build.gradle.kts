@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    androidTarget()
//    androidLibrary {
//        namespace = "warlockfe.warlock3.compose"
//        compileSdk = libs.versions.compileSdk.get().toInt()
//        minSdk = libs.versions.minSdk.get().toInt()
//        androidResources.enable = true
//    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "core"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("commonJvmAndroid") {
                withJvm()
                withAndroidTarget()
                // Following line can be remove when https://issuetracker.google.com/issues/442950553 is fixed
                //withCompilations { it is KotlinMultiplatformAndroidCompilation } // this class is provided by `com.android.kotlin.multiplatform.library`
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":wrayth")) // TODO: remove when abstracting DI

            implementation(libs.kotlinx.serialization.json)

            // Compose
            implementation(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.constraintlayout)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.fastscroller.m3)

            // Third party UI
            implementation(libs.colorpicker)
            implementation(libs.filekit.dialogs)
            implementation(libs.reorderable)

            // Other stuff
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqlite.bundled)
            implementation(libs.coil.compose)
            implementation(libs.room.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("commonJvmAndroidMain") {
            dependencies {
                implementation(libs.coil.network.okhttp)
                implementation(libs.autolink)
            }
        }
        invokeWhenCreated("androidDebug") {
            dependencies {
                // For previews
                implementation(libs.compose.ui.tooling)
            }
        }
    }

    jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    androidResources.enable = true
}

compose {
    resources {
        publicResClass = true
    }
}
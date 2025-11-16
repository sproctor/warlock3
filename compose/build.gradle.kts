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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.constraintlayout)
            //implementation(compose.uiTooling)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.fastscroller.m3)

            // Third party UI
            implementation(libs.colorpicker)
            implementation(libs.filekit.dialogs)

            // Other stuff
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqlite.bundled)
            //implementation(libs.appdirs)
            implementation(libs.coil.compose)
            implementation(libs.room.runtime)
            implementation(libs.kotlin.logging)
            implementation(libs.stately)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
//            implementation(libs.kotlinx.coroutines.test)
//            implementation(libs.turbine)
        }

        val commonJvmAndroidMain by getting {
            dependencies {
                implementation(libs.coil.network.okhttp)
                implementation(libs.autolink)
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
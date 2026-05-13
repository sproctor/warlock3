@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val skipIos = (findProperty("iosSkip") as? String)?.toBoolean() == true

kotlin {
    jvm()
    androidTarget()
//    androidLibrary {
//        namespace = "warlockfe.warlock3.compose"
//        compileSdk = libs.versions.compileSdk.get().toInt()
//        minSdk = libs.versions.minSdk.get().toInt()
//        androidResources.enable = true
//    }
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
                // Following line can be remove when https://issuetracker.google.com/issues/442950553 is fixed
                // withCompilations { it is KotlinMultiplatformAndroidCompilation } // this class is provided by `com.android.kotlin.multiplatform.library`
            }
            group("mobile") {
                withAndroidTarget()
                // Keep the ios subgroup declared even when iOS targets are skipped so
                // the mobileMain source set is still created for androidMain.
                group("ios") {
                    withIos()
                }
            }
        }
    }

    sourceSets {
        if (skipIos) {
            // The default hierarchy template only creates the intermediate `mobileMain`
            // source set when multiple targets share it. With iOS skipped, only Android
            // is in the mobile group, so we wire it up manually — otherwise androidMain
            // can't find the mobile-shared sources under src/mobileMain/.
            val mobileMain =
                maybeCreate("mobileMain").apply {
                    dependsOn(getByName("commonMain"))
                }
            getByName("androidMain").dependsOn(mobileMain)
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":wrayth")) // TODO: remove when abstracting DI

            implementation(libs.kotlinx.serialization.json)

            // Compose
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.constraintlayout)
            implementation(libs.compose.ui.tooling.preview)

            // Third party UI
            implementation(libs.colorpicker)
            implementation(libs.filekit.dialogs)

            // Other stuff
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqlite.bundled)
            implementation(libs.coil.compose)
            implementation(libs.room.runtime)
        }
        getByName("mobileMain") {
            dependencies {
                implementation(libs.compose.material3)
                implementation(libs.fastscroller.core)
            }
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
        jvmMain.dependencies {
            implementation(libs.jewel.standalone)
            // Jewel references AllIconsKeys (e.g. ComboBox chevron) but its standalone POM
            // does not pull the platform icons jar, so the SVG resources have to be added
            // explicitly or every IntelliJ-icon-keyed Icon renders as a magenta placeholder.
            implementation(libs.intellij.platform.icons)
        }
        invokeWhenCreated("androidDebug") {
            dependencies {
                // For previews
                implementation(libs.compose.ui.tooling)
            }
        }
        if (!skipIos) {
            iosMain.dependencies {
                implementation(project(":scripting"))
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

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }
    androidResources.enable = true
}

compose {
    resources {
        publicResClass = true
    }
}

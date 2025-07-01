plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

val jvmToolchainVersion: String by project

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":macro"))
                implementation(project(":stormfront")) // TODO: remove when abstracting DI

                // Compose
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.material.icons)
                api(libs.androidx.lifecycle.viewmodel.compose)
                implementation("tech.annexflow.compose:constraintlayout-compose-multiplatform:0.6.0")
                implementation(compose.uiTooling)
                implementation(compose.components.uiToolingPreview)

                // Third party UI
                implementation(libs.colorpicker)
                implementation(libs.filekit.dialogs)

                // Other stuff
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.sqlite.bundled)
                implementation(libs.appdirs)
            }
        }
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation(libs.kotlinx.coroutines.test)
//                implementation(libs.turbine)
//            }
//        }
    }

    jvmToolchain(jvmToolchainVersion.toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // consumerProguardFiles("consumer-rules.pro")
    }
}

compose {
    resources {
        publicResClass = true
    }
}
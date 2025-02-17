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
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.compose.color.picker)
                implementation(libs.kotlinx.collections.immutable)
                implementation(compose.components.resources)
                implementation(libs.appdirs)
                api(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.sqlite.bundled)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
//                implementation(libs.coil.compose)
//                implementation(libs.coil.svg)
            }
        }
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation(libs.kotlinx.coroutines.test)
//                implementation(libs.turbine)
//            }
//        }
        val jvmMain by getting {
            dependencies {
                // This shouldn't be needed after upgrading to sqlite 2.5.0-beta02
                implementation(libs.sqlite.jvm)
                implementation(compose.preview)
            }
        }
    }

    jvmToolchain(jvmToolchainVersion.toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk = 34

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
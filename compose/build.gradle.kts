plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.library)
}

val jvmToolchainVersion: String by project

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.compose.color.picker)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
//                implementation(libs.androidx.core.ktx)
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
                implementation(compose.preview)
            }
        }
    }

    jvmToolchain(jvmToolchainVersion.toInt())
}

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }
}
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.moko.resources)
}

val jvmToolchainVersion: String by project

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.compose.color.picker)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.moko.resources)
                implementation(libs.moko.resources.compose)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(project(":macro"))
                implementation(project(":stormfront")) // TODO: remove when abstracting DI
                implementation(libs.coil.compose)
                implementation(libs.coil.svg)
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
                implementation(project(":macro"))
                implementation(project(":stormfront")) // TODO: remove when abstracting DI
            }
        }
    }

    jvmToolchain(jvmToolchainVersion.toInt())
}

android {
    namespace = "warlockfe.warlock3.compose"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }
}

multiplatformResources {
    resourcesPackage = "warlockfe.warlock3.compose.resources" // required
}
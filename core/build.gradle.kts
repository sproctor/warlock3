plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room.schema)
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)
                api(libs.kotlin.logging)

                // Preferences
                api(libs.room.runtime)

                implementation(libs.appdirs)

                api(libs.okio)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

dependencies {
    // Room
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

android {
    namespace = "warlockfe.warlock3.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // consumerProguardFiles("consumer-rules.pro")
    }
}

room {
    schemaDirectory("$projectDir/room")
}

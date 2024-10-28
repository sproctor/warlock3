plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.library)
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
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.async)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.sqldelight.primitive.adapters)

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

android {
    namespace = "warlockfe.warlock3.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("warlockfe.warlock3.core.prefs.sql")
            dialect(libs.sqlite.dialect)
            // verifyMigrations = true
        }
    }
}

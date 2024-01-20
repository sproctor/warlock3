plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin {
                srcDir(layout.buildDirectory.dir("generated/antlr"))
            }
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)

                // Preferences
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.async)
                implementation(libs.sqldelight.coroutines)

                api(libs.okio)
            }
        }
    }
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
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

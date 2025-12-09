import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hydraulic.conveyor)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":wrayth"))
    implementation(project(":scripting"))
    implementation(project(":compose"))

    implementation(libs.jewel.standalone)
    implementation(libs.jewel.decorated.window)

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)

    // Command line options
    implementation(libs.clikt)

    // Files
    implementation(libs.appdirs)
    implementation(libs.filekit.dialogs)

    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    // Logging and error reporting
    implementation(libs.slf4j.simple)
    implementation(libs.sentry.kotlin)

    // Control updates
    implementation(libs.conveyor.control)

    // Required by conveyor
    linuxAmd64(libs.compose.desktop.linux.x64)
    linuxAarch64(libs.compose.desktop.linux.arm64)
    macAmd64(libs.compose.desktop.macos.x64)
    macAarch64(libs.compose.desktop.macos.arm64)
    windowsAmd64(libs.compose.desktop.windows.x64)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jvmToolchainVersion.get().toInt())
        vendor = JvmVendorSpec.JETBRAINS
    }
}

compose {
    desktop {
        application {
            mainClass = "warlockfe.warlock3.app.MainKt"

            nativeDistributions {
                packageName = "warlock"
                packageVersion = project.version.toString()
                copyright = "Copyright 2025 Sean Proctor"
                licenseFile.set(project.file("../LICENSE"))
                description = "Warlock Front-end"
                vendor = "Warlock Project"

                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

                // args("--input=/home/sproctor/Downloads/20251116072204.log")
                // args("--input=/home/sproctor/.local/state/warlock/logs/DR_Tefrin/20251122120309.log")
                // args("--sge-port=7900", "--sge-secure=off")
                // args("--connection=Tefrin")

                windows {
                    menu = true
                    // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                    upgradeUuid = "939087B2-4E18-49D1-A55C-1F0BFB116664"
                }
                macOS {
                    bundleID = "warlockfe.warlock3"
                    signing {
                        sign.set(true)
                        identity.set("Sean Proctor")
                    }
                    notarization {
                        appleID.set("sproctor@gmail.com")
                        password.set(providers.environmentVariable("NOTARY_PWD"))
                        teamID.set("DBNJ4AR55X")
                    }
                }
                linux {
                    // Add this for FileKit
                    modules("jdk.security.auth")
                }
            }

            buildTypes.release.proguard {
                configurationFiles.from("rules.pro")
            }
        }
    }

    resources {
        packageOfResClass = "warlockfe.warlock3.app.resources"
    }
}

// region Work around temporary Compose bugs.
configurations.all {
    attributes {
        // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}
// endregion

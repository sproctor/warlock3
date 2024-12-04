import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hydraulic.conveyor)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":stormfront"))
    implementation(project(":scripting"))
    implementation(project(":compose"))

    implementation(compose.desktop.currentOs)
    implementation(compose.uiTooling)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.compose.color.picker)
    implementation(libs.sqldelight.driver.jvm)
    implementation(libs.kotlinx.cli)
    implementation(libs.appdirs)
    implementation(libs.slf4j.simple)

    // Control updates
    implementation(libs.conveyor.control)

    // Required by conveyor
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

kotlin {
    val jvmToolchainVersion: String by project
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(jvmToolchainVersion.toInt())
        vendor = JvmVendorSpec.JETBRAINS
    }
}

compose.desktop {
    application {
        mainClass = "warlockfe.warlock3.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "warlock3"
            packageVersion = project.version.toString()
            modules("java.sql", "java.naming")
            copyright = "Copyright 2024 Sean Proctor"
            licenseFile.set(project.file("../LICENSE.txt"))
            description = "Warlock Front-end"
            vendor = "Warlock Project"

            windows {
                menu = true
                // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                upgradeUuid = "939087B2-4E18-49D1-A55C-1F0BFB116664"
                iconFile.set(project.file("src/main/resources/images/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/images/icon.png"))
            }
            macOS {
                bundleID = "warlockfe.warlock3"
                iconFile.set(project.file("src/main/resources/images/icon.icns"))
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
        }

        buildTypes.release.proguard {
            configurationFiles.from("rules.pro")
        }
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

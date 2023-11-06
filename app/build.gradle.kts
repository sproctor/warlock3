import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
}

val buildConfigDir
    get() = project.layout.buildDirectory.dir("generated/buildconfig")
val buildConfig = tasks.register("buildConfig", GenerateBuildConfig::class.java) {
    classFqName.set("cc.warlock.warlock3.WarlockBuildConfig")
    generatedOutputDir.set(buildConfigDir)
//    fieldsToGenerate.put("composeVersion", BuildProperties.composeVersion(project))
//    fieldsToGenerate.put("composeGradlePluginVersion", BuildProperties.deployVersion(project))
    fieldsToGenerate.put("warlockVersion", project.version)
}
tasks.named("compileKotlin") {
    dependsOn(buildConfig)
}
sourceSets.main.configure {
    java.srcDir(buildConfigDir)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":stormfront"))

    implementation(compose.desktop.currentOs)
    implementation(compose.uiTooling)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.compose.color.picker)
    implementation(libs.sqlite.driver)
    implementation(libs.sqldelight.primitive.adapters)
    implementation(libs.kotlinx.cli)
}

kotlin {
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

compose.desktop {
    application {
        mainClass = "cc.warlock.warlock3.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "warlock3"
            packageVersion = project.version.toString()
            modules("java.sql")
            copyright = "Copyright 2023 Sean Proctor"
            licenseFile.set(project.file("gpl-2.0.txt"))
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
                bundleID = "cc.warlock.warlock3"
                iconFile.set(project.file("src/main/resources/images/icon.icns"))
                signing {
                    sign.set(true)
                    identity.set("Sean Proctor")
                }
                notarization {
                    appleID.set("sproctor@gmail.com")
                    password.set("@keychain:NOTARY_PWD")
                    teamID.set("DBNJ4AR55X")
                }
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(true)
            configurationFiles.from("rules.pro")
        }
    }
}
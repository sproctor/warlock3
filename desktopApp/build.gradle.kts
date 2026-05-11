import io.github.kdroidfilter.nucleus.desktop.application.dsl.ReleaseChannel
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.nucleus)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":wrayth"))
    implementation(project(":scripting"))
    implementation(project(":compose"))

    implementation(compose.desktop.currentOs)

    implementation(libs.nucleus.decorated.window.jewel)
    implementation(libs.nucleus.decorated.window.jbr)

    implementation(libs.jewel.standalone)

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

    // In-app updates
    implementation(libs.nucleus.updater.runtime)
}

kotlin {
    jvmToolchain {
        languageVersion =
            JavaLanguageVersion.of(
                libs.versions.jvmToolchainVersion
                    .get()
                    .toInt(),
            )
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// TODO: verify version fix nucleus tag pattern
val releaseVersion: String =
    System
        .getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: project.version.toString().takeIf { it != "unspecified" }
        ?: "0.0.0"

// Dmg/Msi accept only MAJOR.MINOR.PATCH; strip any "-beta3"-style suffix.
val numericPackageVersion: String = releaseVersion.substringBefore('-')

nucleus.application {
    mainClass = "warlockfe.warlock3.app.MainKt"

    // SQLite calls a restricted method
    jvmArgs += "--enable-native-access=ALL-UNNAMED"

    nativeDistributions {

        // args("--input=/home/sproctor/Downloads/20251116072204.log") // Long log for testing perf
        // args("--input=/home/sproctor/.local/state/warlock/logs/DR_Tefrin/20251122120309.log") // quick log
        // args("--sge-port=7900", "--sge-secure=off")
        // args("--connection=Tefrin")

        targetFormats(
            TargetFormat.Dmg,
            TargetFormat.Zip, // required alongside Dmg for macOS auto-update
            TargetFormat.Nsis,
            TargetFormat.Msi,
            TargetFormat.Deb,
            TargetFormat.AppImage,
            TargetFormat.Tar,
        )

        appName = "Warlock"
        packageName = "warlock"
        packageVersion = releaseVersion
        description = "Warlock Front-end"
        vendor = "Warlock Project"
        copyright = "Copyright 2026 Sean Proctor"
        homepage = "https://warlockfe.github.io/"
        licenseFile.set(project.file("../LICENSE"))

        modules(
            "jdk.accessibility",
            "java.instrument",
            "java.management",
            "jdk.dynalink",
            "jdk.security.auth",
            "jdk.unsupported",
        )

        cleanupNativeLibs = true
        artifactName = "\${name}-\${version}-\${os}-\${arch}.\${ext}"

        publish {
            github {
                enabled = true
                owner = "sproctor"
                repo = "warlock3"
                channel =
                    when {
                        releaseVersion.contains("beta") -> ReleaseChannel.Beta
                        releaseVersion.contains("alpha") -> ReleaseChannel.Alpha
                        else -> ReleaseChannel.Latest
                    }
            }
        }

        windows {
            // Windows .exe VERSIONINFO requires numeric-only version, so use the
            // stripped version for both the app-image executable and installers.
            packageVersion = numericPackageVersion
            msiPackageVersion = numericPackageVersion
            exePackageVersion = numericPackageVersion

            iconFile.set(project.file("../icons/icon.ico"))
            menu = true
            // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
            upgradeUuid = "939087B2-4E18-49D1-A55C-1F0BFB116664"

            // Azure Trusted Signing — Auth comes from the standard Azure
            // service-principal env vars (AZURE_CLIENT_ID /
            // AZURE_CLIENT_SECRET) consumed by electron-builder's signing tool,
            // or from an interactive `az login` session.
            // Signing is gated on the az CLI being able to obtain a token for
            // the code-signing resource, so local builds without Azure access
            // still produce unsigned binaries.
            signing {
                enabled = true
                azureTenantId = "dfe2ba9a-411b-4907-b6b0-59fe59aefc2d"
                azureEndpoint = "https://eus.codesigning.azure.net"
                azureCodeSigningAccountName = "scrapgolem"
                azureCertificateProfileName = "signing"
                timestampServer = "http://timestamp.digicert.com"
            }
        }
        macOS {
            // CFBundleVersion (used by the .app bundle, not just the DMG) must be
            // dotted integers, so apply the numeric version at the OS level too.
            packageVersion = numericPackageVersion
            dmgPackageVersion = numericPackageVersion

            iconFile.set(project.file("../icons/icon.icns"))
            bundleID = "warlockfe.warlock3"
            // CI signs macOS post-lipo via the build-macos-universal action; only
            // configure jpackage-time signing for local dev builds.
            if (System.getenv("CI") != "true") {
                signing {
                    sign.set(true)
                    identity.set("Developer Application ID: Sean Proctor (DBNJ4AR55X")
                }
                notarization {
                    appleID.set("sproctor@gmail.com")
                    password.set(providers.environmentVariable("NOTARY_PWD"))
                    teamID.set("DBNJ4AR55X")
                }
            }
        }
        linux {
            iconFile.set(project.file("../icons/icon-512.png"))
            debMaintainer = "Sean Proctor <sproctor@gmail.com>"
        }
    }

    buildTypes.release.proguard {
        configurationFiles.from("rules.pro")
    }
}

compose {
    resources {
        packageOfResClass = "warlockfe.warlock3.app.resources"
    }
}

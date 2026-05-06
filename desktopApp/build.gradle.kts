import io.github.kdroidfilter.nucleus.desktop.application.dsl.SigningAlgorithm
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

    implementation(libs.jewel.standalone)
    implementation(libs.jewel.decorated.window)

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

val releaseVersion: String =
    System
        .getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: project.version.toString()

nucleus.application {
    mainClass = "warlockfe.warlock3.app.MainKt"

    nativeDistributions {
        targetFormats(
            TargetFormat.Dmg,
            TargetFormat.Zip, // required alongside Dmg for macOS auto-update
            TargetFormat.Msi,
            TargetFormat.Deb,
        )

        // args("--input=/home/sproctor/Downloads/20251116072204.log") // Long log for testing perf
        // args("--input=/home/sproctor/.local/state/warlock/logs/DR_Tefrin/20251122120309.log") // quick log
        // args("--sge-port=7900", "--sge-secure=off")
        // args("--connection=Tefrin_DR")

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
            }
        }

        windows {
            // TODO: add a .ico to icons/ and set iconFile here; defaults are used otherwise.
            menu = true
            // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
            upgradeUuid = "939087B2-4E18-49D1-A55C-1F0BFB116664"

            // PFX signing — CI decodes WIN_CSC_LINK (base64 secret) to a file
            // and points WIN_CSC_LINK at that path before invoking gradle.
            // To use Azure Trusted Signing instead, replace the block below
            // with `azureTenantId`, `azureEndpoint`, `azureCertificateProfileName`,
            // `azureCodeSigningAccountName` (see Nucleus code-signing docs).
            val pfxPath = System.getenv("WIN_CSC_LINK")
            val pfxPassword = System.getenv("WIN_CSC_KEY_PASSWORD")
            if (!pfxPath.isNullOrBlank() && !pfxPassword.isNullOrBlank() && file(pfxPath).exists()) {
                signing {
                    enabled = true
                    certificateFile.set(file(pfxPath))
                    certificatePassword = pfxPassword
                    algorithm = SigningAlgorithm.Sha256
                    timestampServer = "http://timestamp.digicert.com"
                }
            }
        }
        macOS {
            // TODO: add a .icns to icons/ and set iconFile here; defaults are used otherwise.
            bundleID = "warlockfe.warlock3"
            // CI signs macOS post-lipo via the build-macos-universal action; only
            // configure jpackage-time signing for local dev builds.
            if (System.getenv("CI") != "true") {
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
        linux {
            iconFile.set(project.file("../icons/icon-512.png"))
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

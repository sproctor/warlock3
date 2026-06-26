import com.seanproctor.potassium.dsl.LinuxTargetFormat
import com.seanproctor.potassium.dsl.MacOSTargetFormat
import com.seanproctor.potassium.dsl.ReleaseChannel
import com.seanproctor.potassium.dsl.ReleaseType
import com.seanproctor.potassium.dsl.WindowsTargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.potassium)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":wrayth"))
    implementation(project(":scripting"))
    implementation(project(":compose"))

    implementation(compose.desktop.currentOs)

    implementation(libs.jewel.decorated.window)

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
    implementation(libs.potassium.updater)
}

kotlin {
    jvmToolchain {
        languageVersion =
            JavaLanguageVersion.of(
                libs.versions.jvmToolchainVersion
                    .get()
                    .toInt(),
            )
    }
}

// TODO: verify version fits potassium tag pattern
val resolvedReleaseVersion: String? =
    System
        .getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: project.version.toString().takeIf { it != "unspecified" }

val releaseVersion: String = resolvedReleaseVersion ?: "0.0.0-dev"

// JBR 25 is what gets bundled into installers (via Potassium) and
// what `:desktopApp:run` launches under for local dev. Compilation happens
// under the project toolchain (Temurin) - JBR is only used at runtime.
val jbrRuntime =
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }

potassium {
    mainClass = "warlockfe.warlock3.app.MainKt"

    // Run + package under JBR 25 even though the rest of the build uses
    // Temurin 21 (see toolchain config above).
    javaHome =
        jbrRuntime
            .get()
            .metadata.installationPath.asFile.absolutePath

    // SQLite calls a restricted method
    jvmArgs += "--enable-native-access=ALL-UNNAMED"
    // Opt-in GC + safepoint logging for diagnosing stream-render stalls:
    //   ./gradlew :desktopApp:run -PgcLog
    // Writes per-pid logs (so multiple connections/processes don't collide) under the build dir with
    // wall-clock timestamps, to correlate against StreamWorkQueue "STALL" lines. Diagnostic only.
    if (project.hasProperty("gcLog")) {
        val gcLogPath =
            project.layout.buildDirectory
                .get()
                .asFile
                .resolve("gc-%p.log")
        jvmArgs += "-Xlog:gc*,safepoint:file=$gcLogPath:time,uptime,level,tags:filecount=5,filesize=20M"
    }
    // Confirmation experiment: ./gradlew :desktopApp:run -Pzgc swaps in the low-pause collector. If the
    // lag/STALL lines vanish under ZGC, the stalls were GC pauses; if they persist, it is pool/scheduling.
    if (project.hasProperty("zgc")) {
        jvmArgs += "-XX:+UseZGC"
    }

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
            releaseType =
                when (channel) {
                    ReleaseChannel.Latest -> ReleaseType.Release
                    else -> ReleaseType.Prerelease
                }
        }
    }

    windows {
        // NSIS installer
        targetFormats(WindowsTargetFormat.Nsis)

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
        // Zip alongside Dmg so the macOS auto-updater has a non-installer artifact to pull.
        targetFormats(MacOSTargetFormat.Dmg, MacOSTargetFormat.Zip)

        iconFile.set(project.file("../icons/icon.icns"))
        bundleID = "warlockfe.warlock3"
        // Sign + notarize each per-arch build at package time (no universal/lipo step anymore).
        // In CI the Developer ID Application cert is imported into a temporary keychain whose path
        // arrives via MAC_KEYCHAIN_PATH; locally the identity resolves from the login keychain.
        // Only the macOS packaging tasks consume this, so it is inert on Linux/Windows runners.
        signing {
            sign.set(true)
            identity.set("Developer ID Application: Sean Proctor (DBNJ4AR55X)")
            System.getenv("MAC_KEYCHAIN_PATH")?.takeIf { it.isNotBlank() }?.let { keychain.set(it) }
        }
        notarization {
            appleID.set("sproctor@gmail.com")
            // App-specific password for notarytool; absent locally means notarization is skipped.
            password.set(providers.environmentVariable("NOTARY_PWD"))
            teamID.set("DBNJ4AR55X")
        }
    }
    linux {
        targetFormats(LinuxTargetFormat.Deb, LinuxTargetFormat.AppImage, LinuxTargetFormat.Tar)

        iconFile.set(project.file("../icons/icon-512.png"))
        debMaintainer = "Sean Proctor <sproctor@gmail.com>"
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

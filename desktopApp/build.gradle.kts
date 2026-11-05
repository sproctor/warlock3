import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.AbstractComposeHotRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.nucleus)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":wrayth"))
    implementation(project(":compose"))

    implementation(compose.desktop.currentOs)

    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)

    implementation(libs.jewel.standalone)

    implementation(libs.compose.components.resources)

    // Command line options
    implementation(libs.clikt)

    // Files
    implementation(libs.appdirs)
    implementation(libs.filekit.dialogs)

    implementation(libs.kotlinx.serialization.json)
    // Opens the preferences database (Room.databaseBuilder in Main.kt)
    implementation(libs.room.runtime)

    // Logging is Kermit. :core carries the slf4j provider that routes slf4j-using libraries into
    // it. Crash reporting comes from :compose (see SentryCrashReporter.kt).
    implementation(libs.kermit)

    // In-app updates
    implementation(libs.nucleus.updater)
    implementation(libs.nucleus.core.runtime)

    testImplementation(kotlin("test"))
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

// TODO: verify version fits nucleus tag pattern
val resolvedReleaseVersion: String? =
    System
        .getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: project.version.toString().takeIf { it != "unspecified" }

val releaseVersion: String = resolvedReleaseVersion ?: "0.0.0-dev"

// Releases ship a native image, so no JVM reaches users. `:desktopApp:run` is the one thing that
// starts the app on a JVM, and it needs 25 because Jewel ships class file 69, while the rest of the
// build compiles under the project toolchain. Set on the task rather than through nucleus's javaHome,
// which is a plain String and would resolve this toolchain on every build that configures the project.
// In afterEvaluate because nucleus registers run there and points it at the compile toolchain.
val devRuntime = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }

afterEvaluate {
    tasks.named<JavaExec>("run") {
        javaLauncher = devRuntime
    }
}

// Compose Hot Reload defaults to whatever JetBrains Runtime it finds, which lands on JBR 21 here and
// dies with "JewelTheme has been compiled by a more recent version of the Java Runtime (class file 69.0)".
tasks.withType<AbstractComposeHotRun>().configureEach {
    javaLauncher = devRuntime
}

nucleus.application {
    mainClass = "warlockfe.warlock3.app.MainKt"

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

    // The release build: a native image of the desktop app (Tao windows, no AWT), packaged by the
    // packageGraalvm* tasks. The tracing agent's metadata (`runWithNativeAgent`) is merged into
    // src/main/resources/META-INF/native-image.
    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        imageName = "warlock"
        // The game protocol is decoded as windows-1252, which native-image leaves out by default.
        allCharsets = true
        nativeImageConfigBaseDir.set(layout.projectDirectory.dir("src/main/resources/META-INF/native-image/warlock"))
    }

    nativeDistributions {
        // Nucleus takes one format list and builds the current OS's share of it. Zip and Tar are
        // current-OS archive formats, so each is added only on the OS that ships it: Zip alongside Dmg
        // so the macOS auto-updater has a non-installer artifact to pull, Tar for Linux.
        //
        // Dmg is macOS-only here because of notarization. Nucleus disables packageDmg off macOS but
        // still registers notarizeDmg, which runs whenever NOTARY_PWD is set, as it is on every
        // release runner, and fails validation on the dmg directory that was never created.
        val os = System.getProperty("os.name").lowercase()
        targetFormats(
            *buildList {
                add(TargetFormat.Nsis)
                add(TargetFormat.Deb)
                add(TargetFormat.AppImage)
                if (os.contains("mac")) {
                    add(TargetFormat.Dmg)
                    add(TargetFormat.Zip)
                }
                if (os.contains("linux")) add(TargetFormat.Tar)
            }.toTypedArray(),
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
                releaseType =
                    when (channel) {
                        ReleaseChannel.Latest -> ReleaseType.Release
                        else -> ReleaseType.Prerelease
                    }
            }
        }

        windows {
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
            iconFile.set(project.file("../icons/icon-512.png"))
            debMaintainer = "Sean Proctor <sproctor@gmail.com>"
        }
    }

    // Nucleus enables ProGuard on the release build type by default; disable it
    // explicitly so no *Release* packaging task can minify. (ProGuard here only shrank ~13%, with
    // obfuscation and optimization already off, so it wasn't worth the keep-rule
    // maintenance and the JDK-version lag it introduced.)
    buildTypes.release.proguard {
        isEnabled.set(false)
    }
}

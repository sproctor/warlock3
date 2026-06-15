plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

// Resolve the release version the same way desktopApp does: prefer the RELEASE_VERSION
// env var (set from the git tag in CI), fall back to project.version, else 0.0.0.
val resolvedReleaseVersion: String =
    System
        .getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: project.version.toString().takeIf { it != "unspecified" }
        ?: "0.0.0"

// Play requires a monotonically increasing integer versionCode. The old semver-derived
// scheme (major*1_000_000 + minor*1_000 + patch) couldn't distinguish beta builds of the
// same version, so every 3.1.0-beta.* mapped to 3_001_000 and Play rejected re-uploads.
// Instead, count commits (which only grows) and offset past the highest code that scheme
// ever produced so every new build outranks it and each commit bumps the code.
val versionCodeBaseline = 3_000_000
val derivedVersionCode: Int =
    run {
        val commitCount =
            try {
                providers
                    .exec {
                        commandLine("git", "rev-list", "--count", "HEAD")
                    }.standardOutput.asText
                    .get()
                    .trim()
                    .toIntOrNull()
            } catch (_: Exception) {
                null
            } ?: 0
        versionCodeBaseline + commitCount
    }

android {
    namespace = "warlockfe.warlock3.android"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        applicationId = "warlockfe.warlock3"
        versionCode = derivedVersionCode
        versionName = resolvedReleaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release upload-key signing. Credentials come from env vars (set as GitHub
    // Actions secrets in CI) or, for local release builds, the matching Gradle
    // properties. The keystore is the *upload* key; Google re-signs with the app
    // signing key via Play App Signing. When no keystore is configured the release
    // build is left unsigned (useful for local `bundleRelease` smoke tests).
    val keystorePath =
        System.getenv("ANDROID_KEYSTORE_PATH")
            ?: project.findProperty("warlock.keystore.path") as String?
    if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword =
                    System.getenv("ANDROID_KEYSTORE_PASSWORD")
                        ?: project.findProperty("warlock.keystore.password") as String?
                keyAlias =
                    System.getenv("ANDROID_KEY_ALIAS")
                        ?: project.findProperty("warlock.key.alias") as String?
                keyPassword =
                    System.getenv("ANDROID_KEY_PASSWORD")
                        ?: project.findProperty("warlock.key.password") as String?
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        getByName("debug") {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
    }
    kotlin {
        jvmToolchain(
            libs.versions.jvmToolchainVersion
                .get()
                .toInt(),
        )
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":compose"))
    implementation(project(":wrayth"))
    implementation(project(":scripting"))

    implementation(libs.kotlinx.serialization.json)

    // Compose dependencies
    implementation(libs.compose.material3)

    // Android presentation components
    implementation(libs.androidx.activity.compose)

    // Splash screen compatibility pre-android 12
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    implementation(libs.filekit.dialogs)

    // Leak detection
//    debugImplementation(Square.leakCanary.android)
}

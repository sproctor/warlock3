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

// Play requires a monotonically increasing integer versionCode. Derive it from the
// semver release version (suffixes like "-beta.1" are dropped) so a higher tag always
// yields a higher code. Assumes minor/patch each stay below 1000.
val derivedVersionCode: Int =
    run {
        val parts =
            resolvedReleaseVersion
                .substringBefore('-')
                .split('.')
                .mapNotNull { it.toIntOrNull() }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        (major * 1_000_000 + minor * 1_000 + patch).coerceAtLeast(1)
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

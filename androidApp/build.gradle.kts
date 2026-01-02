plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
//    alias(libs.plugins.kotlin.serialization)
//    alias(libs.plugins.ksp)
//    alias(libs.plugins.gms.google.services)
//    alias(libs.plugins.triplet.play)
}

android {
    namespace = "warlockfe.warlock3.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        applicationId = "warlockfe.warlock3"
        versionCode = 1
        versionName = version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

//    if (project.hasProperty("RELEASE_STORE_FILE")) {
//        signingConfigs {
//            create("release") {
//                storeFile = file(project.findProperty("RELEASE_STORE_FILE") ?: "")
//                storePassword = project.findProperty("RELEASE_STORE_PASSWORD")?.toString() ?: ""
//                keyAlias = project.findProperty("RELEASE_KEY_ALIAS")?.toString() ?: ""
//                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD")?.toString() ?: ""
//            }
//        }
//    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
//            if (project.hasProperty("RELEASE_STORE_FILE")) {
//                signingConfig = signingConfigs.getByName("release")
//            }
        }
        getByName("debug") {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
    }
    kotlin {
        jvmToolchain(libs.versions.jvmToolchainVersion.get().toInt())
    }
    buildFeatures {
        buildConfig = true
    }
}

//project.findProperty("ANDROID_PUBLISHER_CREDENTIALS")?.let { filename ->
//    play {
//        track.set("internal") //'alpha','beta' or 'production'
//        serviceAccountCredentials.set(file(filename.toString()))
//        defaultToAppBundles.set(true)
//    }
//}

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

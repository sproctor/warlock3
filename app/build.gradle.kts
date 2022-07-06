import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":stormfront"))

    implementation("org.slf4j:slf4j-api:_")
    implementation(KotlinX.coroutines.core)
    implementation(Kotlin.stdlib.jdk8)
    implementation(compose.desktop.currentOs)
    implementation(compose.uiTooling)
    // implementation("org.jetbrains.compose.components:components-splitpane:_")
    implementation("com.godaddy.android.colorpicker:compose-color-picker-jvm:_")
    implementation("com.squareup.sqldelight:sqlite-driver:_")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:_")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
}

compose.desktop {
    application {
        mainClass = "cc.warlock.warlock3.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "warlock3"
            packageVersion = "1.0.13"
            modules("java.sql")
            copyright = "Copyright 2022 Sean Proctor"
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
                iconFile.set(project.file("src/main/resources/images/icon.icns"))
            }
        }
    }
}
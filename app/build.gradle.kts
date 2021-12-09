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
    implementation("com.uchuhimo:konf-hocon:_")
    implementation(KotlinX.coroutines.core)
    implementation(Kotlin.stdlib.jdk8)
    implementation(compose.desktop.currentOs)
    implementation(compose.uiTooling)
    // implementation("org.jetbrains.compose.components:components-splitpane:_")
    implementation("org.pushing-pixels:aurora-theming:_")
    implementation("org.pushing-pixels:aurora-component:_")
    implementation("org.pushing-pixels:aurora-window:_")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
}

compose.desktop {
    application {
        mainClass = "cc.warlock.warlock3.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "warlock3"
            packageVersion = "1.0.8"

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
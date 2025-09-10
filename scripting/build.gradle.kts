import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.antlr.kotlin)
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
        include("**/*.g4")
    }

    // We want the generated source files to have this package name
    val pkgName = "warlockfe.warlock3.scripting.parsers.generated"
    packageName = pkgName

    // We want visitors alongside listeners.
    // The Kotlin target language is implicit, as is the file encoding (UTF-8)
    arguments = listOf("-visitor")

    // Generated files are outputted inside build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "warlockfe.warlock3.scripting"
        compileSdk = 36
        minSdk = 26
    }

    sourceSets {
        commonMain {
            kotlin {
                srcDir(generateKotlinGrammarSource)
            }
            dependencies {
                implementation(project(":core"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.collections.immutable)

                // Parsing
                implementation(libs.antlr.kotlin.runtime)
                implementation(libs.rhino)

                // Needed for JS scripting implementation
                implementation(libs.kotlin.reflect)

                // BigDecimal
                implementation(libs.bignum)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

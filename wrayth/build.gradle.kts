import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.antlr.kotlin)
}

//tasks {
//    generateGrammarSource {
//        arguments = arguments + listOf("-visitor", "-long-messages", "-package", "warlockfe.warlock3.wrayth.parser")
//        outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/warlockfe/warlock3/wrayth/parser/")
//    }
//
//    compileKotlin {
//        dependsOn.add(generateGrammarSource)
//    }
//
//    compileTestKotlin {
//        dependsOn.add(generateTestGrammarSource)
//    }
//}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
        include("**/*.g4")
    }

    // We want the generated source files to have this package name
    val pkgName = "warlockfe.warlock3.wrayth.parsers.generated"
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
        namespace = "warlockfe.warlock3.wrayth"
        compileSdk = 36
        minSdk = 26
    }

    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())

    sourceSets {
        commonMain {
            kotlin {
                srcDir(generateKotlinGrammarSource)
            }
            dependencies {
                implementation(project(":core"))
                implementation(project(":scripting"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.xmlutil.serialization)
                implementation(libs.antlr.kotlin.runtime)
                implementation(libs.apache.commons.text)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.io.core)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

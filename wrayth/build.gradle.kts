plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

dependencies {
    implementation(project(":core"))
    implementation(project(":scripting"))
    implementation(libs.kotlinx.coroutines.core)
    antlr(libs.antlr4)
    implementation(libs.apache.commons.text)
}

tasks {
    generateGrammarSource {
        arguments = arguments + listOf("-visitor", "-long-messages", "-package", "warlockfe.warlock3.wrayth.parser")
        outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/warlockfe/warlock3/wrayth/parser/")
    }

    compileKotlin {
        dependsOn.add(generateGrammarSource)
    }

    compileTestKotlin {
        dependsOn.add(generateTestGrammarSource)
    }
}

kotlin {
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

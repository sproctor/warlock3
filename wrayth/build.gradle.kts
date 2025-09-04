plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    antlr
}

dependencies {
    implementation(project(":core"))
    implementation(project(":scripting"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.xmlutil.serialization)
    antlr(libs.antlr4)
    implementation(libs.apache.commons.text)
    testImplementation(libs.kotlin.test)
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

plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

dependencies {
    implementation(project(":core"))

    // Parsing
    antlr(libs.antlr4)

    // Testing
    testImplementation(libs.kotlin.test)
}

kotlin {
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

tasks {
    test {
        useJUnitPlatform()
    }

    generateGrammarSource {
        // maxHeapSize = "64m"
        arguments = arguments + listOf("-visitor", "-long-messages", "-package", "warlockfe.warlock3.macro.parser")
        outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/warlockfe/warlock3/macro/parser/")
    }

    compileKotlin {
        dependsOn.add(generateGrammarSource)
    }
}
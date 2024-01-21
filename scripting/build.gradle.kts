plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

dependencies {
    implementation(project(":core"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.collections.immutable)

    // Parsing
    antlr(libs.antlr4)
    implementation(libs.rhino)

    // Needed for JS scripting implementation
    implementation(libs.kotlin.reflect)

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
        arguments = arguments + listOf("-visitor", "-long-messages", "-package", "warlockfe.warlock3.scripting.parser")
        outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/warlockfe/warlock3/scripting/parser/")
    }

    compileKotlin {
        dependsOn.add(generateGrammarSource)
    }
}
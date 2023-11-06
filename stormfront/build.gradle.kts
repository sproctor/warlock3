plugins {
    alias(libs.plugins.kotlin.jvm)
    antlr
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    antlr(libs.antlr4)
    implementation(libs.okio)
    implementation(libs.apache.commons.text)
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.stormfront.parser")
    outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/cc/warlock/warlock3/stormfront/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}

kotlin {
    val jvmToolchainVersion: String by project
    jvmToolchain(jvmToolchainVersion.toInt())
}

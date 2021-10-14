plugins {
    kotlin("jvm")
    antlr
}

dependencies {
    implementation(project(":core"))
    implementation(Kotlin.stdlib.jdk8)
    implementation(KotlinX.coroutines.core)
    antlr("org.antlr:antlr4:_")
    implementation(Square.okio)
    implementation("org.apache.commons:commons-text:_")
}

tasks.generateGrammarSource {
    // maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.stormfront.parser")
    outputDirectory = File("$buildDir/generated-src/antlr/main/cc/warlock/warlock3/stormfront/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}

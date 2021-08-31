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
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    implementation("commons-beanutils:commons-beanutils:_")
    implementation("org.apache.commons:commons-configuration2:_")
}

tasks.generateGrammarSource {
    // maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.stormfront.parser")
    outputDirectory = File("$buildDir/generated-src/antlr/main/cc/warlock/warlock3/stormfront/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}

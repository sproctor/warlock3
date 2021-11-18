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

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
}
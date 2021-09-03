plugins {
    kotlin("jvm")
    antlr
}

dependencies {
    implementation(Kotlin.stdlib.jdk8)
    implementation(KotlinX.coroutines.core)
    //implementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    testImplementation(Testing.junit4)
    antlr("org.antlr:antlr4:_")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.generateGrammarSource {
    // maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.core.parser")
    outputDirectory = File("$buildDir/generated-src/antlr/main/cc/warlock/warlock3/core/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}
plugins {
    kotlin("jvm")
    antlr
}

dependencies {
    implementation(Kotlin.stdlib.jdk8)
    implementation(KotlinX.coroutines.core)
    //implementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    // testImplementation(Testing.junit4)
    testImplementation(kotlin("test"))
    antlr("org.antlr:antlr4:_")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
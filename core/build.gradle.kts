plugins {
    kotlin("jvm")
    antlr
    id("com.squareup.sqldelight")
}

dependencies {
    // Standard libraries
    implementation(Kotlin.stdlib.jdk8)
    implementation("org.jetbrains.kotlin:kotlin-reflect:_")
    implementation(KotlinX.coroutines.core)

    // Parsing
    antlr("org.antlr:antlr4:_")
    implementation("org.mozilla:rhino:_")

    // Preferences
    implementation("com.squareup.sqldelight:sqlite-driver:_")
    implementation("com.squareup.sqldelight:coroutines-extensions-jvm:_")

    // Testing
    // testImplementation(Testing.junit4)
    testImplementation(kotlin("test"))
}

sqldelight {
    database("Database") {
        packageName = "cc.warlock.warlock3.core.prefs.sql"
        dialect = "sqlite:3.24"
        // verifyMigrations = true
    }
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
    kotlinOptions.freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
}

tasks.generateGrammarSource {
    // maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.core.parser")
    outputDirectory = File("$buildDir/generated-src/antlr/main/cc/warlock/warlock3/core/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}
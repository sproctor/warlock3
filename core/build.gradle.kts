plugins {
    kotlin("jvm")
    antlr
    id("app.cash.sqldelight")
}

dependencies {
    // Standard libraries
    implementation(Kotlin.stdlib.jdk8)
    api(KotlinX.coroutines.core)
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:_")

    // Parsing
    antlr("org.antlr:antlr4:_")
    implementation("org.mozilla:rhino:_")

    // Needed for JS scripting implementation
    implementation("org.jetbrains.kotlin:kotlin-reflect:_")

    // Preferences
    implementation("app.cash.sqldelight:sqlite-driver:_")
    implementation("app.cash.sqldelight:async-extensions:_")
    implementation("app.cash.sqldelight:coroutines-extensions-jvm:_")

    // Testing
    testImplementation(kotlin("test"))
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("cc.warlock.warlock3.core.prefs.sql")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:_")
            // verifyMigrations = true
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.generateGrammarSource {
    // maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "cc.warlock.warlock3.core.parser")
    outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/cc/warlock/warlock3/core/parser/")
}

tasks.compileKotlin {
    dependsOn.add(tasks.generateGrammarSource)
}
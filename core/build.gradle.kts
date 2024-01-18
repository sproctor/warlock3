plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
    antlr
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.collections.immutable)

    // Parsing
    antlr(libs.antlr4)
    implementation(libs.rhino)

    // Needed for JS scripting implementation
    implementation(libs.kotlin.reflect)

    // Preferences
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.async)
    implementation(libs.sqldelight.coroutines)

    // Testing
    testImplementation(libs.kotlin.test)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("warlockfe.warlock3.core.prefs.sql")
            dialect(libs.sqlite.dialect)
            // verifyMigrations = true
        }
    }
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
        arguments = arguments + listOf("-visitor", "-long-messages", "-package", "warlockfe.warlock3.core.parser")
        outputDirectory = File("${layout.buildDirectory.get()}/generated-src/antlr/main/warlockfe/warlock3/core/parser/")
    }

    compileKotlin {
        dependsOn.add(generateGrammarSource)
    }
}
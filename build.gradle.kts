buildscript {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
        classpath("org.jetbrains.compose:compose-gradle-plugin:_")
        classpath(Square.sqlDelight.gradlePlugin)
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

subprojects {
    version = "3.0.8"
    group = "cc.warlock.warlock3"
}

tasks.wrapper {
    gradleVersion = "8.3"
}
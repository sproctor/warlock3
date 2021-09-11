buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
        classpath("org.jetbrains.compose:compose-gradle-plugin:_")
    }
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    version = "1.0.0"
    group = "cc.warlock.warlock3"
}

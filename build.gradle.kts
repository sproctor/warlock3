buildscript {
    repositories {
        mavenCentral()
        // maven { url = uri("https://plugins.gradle.org/m2/") }
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
        classpath("org.jetbrains.compose:compose-gradle-plugin:_")
//        classpath 'org.openjfx:javafx-plugin:0.0.10'
//        classpath "com.github.ben-manes:gradle-versions-plugin:0.27.0"
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

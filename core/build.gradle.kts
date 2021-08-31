plugins {
    kotlin("jvm")
}

dependencies {
    implementation(Kotlin.stdlib.jdk8)
    implementation(KotlinX.coroutines.core)
    //implementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    testImplementation(Testing.junit4)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

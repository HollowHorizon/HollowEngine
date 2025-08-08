plugins {
    kotlin("jvm") //version "2.1.20-Beta2"
    kotlin("plugin.serialization") //version "2.1.20-Beta2"
    id("com.google.devtools.ksp") version "2.1.20-Beta2-1.0.30"
}

version = "1.0.0"
group = "ru.hollowhorizon"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.20-Beta2-1.0.30")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

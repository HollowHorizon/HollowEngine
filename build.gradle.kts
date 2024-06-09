plugins {
    // Required for NeoGradle
    id("org.jetbrains.gradle.plugin.idea-ext").version("1.1.7")
    id("org.jetbrains.kotlin.jvm").version("2.0.0")
    id("org.jetbrains.kotlin.plugin.serialization").version("2.0.0")
}

repositories {
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content { includeGroup("thedarkcolour") }
    }
}
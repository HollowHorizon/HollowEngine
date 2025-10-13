plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://maven.kikugie.dev/snapshots/")
    maven("https://maven.minecraftforge.net")
}

dependencies {
    implementation("architectury-plugin:architectury-plugin.gradle.plugin:3.4-SNAPSHOT")
    implementation("dev.architectury:architectury-loom:1.9-SNAPSHOT")
    implementation("net.fabricmc:tiny-remapper:0.10.4")
    implementation("dev.kikugie:stonecutter:0.7.10")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    implementation("me.fallenbreath.yamlang:me.fallenbreath.yamlang.gradle.plugin:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
    }
}
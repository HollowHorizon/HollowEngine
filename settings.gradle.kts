
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://plugins.gradle.org/m2/")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
    }

    val kotlinVersion: String by settings
    val architecturyPluginVersion: String by settings
    val architecturyLoomVersion: String by settings
    val shadowVersion: String by settings
    val yamlangVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("architectury-plugin") version architecturyPluginVersion
        id("dev.architectury.loom") version architecturyLoomVersion
        id("com.github.johnrengelman.shadow") version shadowVersion
        id("me.fallenbreath.yamlang") version yamlangVersion
        id("com.google.devtools.ksp") version "2.3.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include("bootstrap:fabric")
include("bootstrap:neoforge")
include("runtime")
include("bridge")
include("katari-binding-processor")

project(":bootstrap").buildFileName = "parent.gradle.kts"
project(":bootstrap:fabric").projectDir = file("bootstrap-fabric")
project(":bootstrap:neoforge").projectDir = file("bootstrap-neoforge")

val modName: String by settings
rootProject.name = modName

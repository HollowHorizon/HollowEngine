
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
        maven("https://maven.kikugie.dev/snapshots")
    }

    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.10"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include("compiler")

stonecutter {

    kotlinController = true
    centralScript = "build.gradle.kts"
//    shared {
//    }
    create(rootProject) {

        rootProject.projectDir.resolve("versions")
            .listFiles()
            .filter { it.isDirectory }
            .filter { !it.resolve(".build-ignore").exists() }
            .forEach { version(it.name) }
        branch("compiler")
    }
}

val modName: String by settings
rootProject.name = modName
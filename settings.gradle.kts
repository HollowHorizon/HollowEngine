import dev.kikugie.stonecutter.StonecutterSettings

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
    id("dev.kikugie.stonecutter") version "0.5-alpha.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

extensions.configure<StonecutterSettings> {
    kotlinController = true
    centralScript = "build.gradle.kts"
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (it in loaders) vers("$version-$it", version)
        }

        mc("1.21", "fabric", "forge", "neoforge")
        mc("1.20.1", "fabric", "forge")
        mc("1.19.2", "fabric", "forge")
    }
    create(rootProject)
}

rootProject.name = "HollowEngine"

include("compiler-plugin")
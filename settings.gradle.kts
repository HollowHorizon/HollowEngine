pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.parchmentmc.org")
        exclusiveContent {
            forRepository {
                maven("https://maven.fabricmc.net")
            }
            filter {
                includeGroup("net.fabricmc")
                includeGroup("fabric-loom")
            }
        }
        exclusiveContent {
            forRepository {
                maven("https://maven.minecraftforge.net")
            }
            filter {
                includeGroupAndSubgroups("net.minecraftforge")
            }
        }


        exclusiveContent {
            forRepository {
                maven("https://maven.neoforged.net/releases")
            }
            filter {
                includeGroupAndSubgroups("net.neoforged")
                includeGroup("codechicken")
            }
        }
        exclusiveContent {
            forRepository {
                maven("https://repo.spongepowered.org/repository/maven-public")
            }
            filter {
                includeGroupAndSubgroups("org.spongepowered")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
}

rootProject.name = "HollowEngine"

include("common")
include("fabric")
include("forge")
include("neoforge")

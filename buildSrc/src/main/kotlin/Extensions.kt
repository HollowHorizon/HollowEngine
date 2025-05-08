import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.exclude

var isForgelike = false

fun DependencyHandlerScope.install(path: String, includeInJar: Boolean = true, isMod: Boolean = false) {
    val dependency = if (isMod) modImplementation(path) else "implementation"(path) {
        exclude("org.jetbrains.kotlin")
        exclude("org.lwjgl")
        exclude("org.ow2.asm")
        exclude("net.sourceforge.jaad.aac")
        exclude("org.slf4j")
        exclude("commons-logging")
    }

    dependency.takeIf { isForgelike && !isMod }?.let { "forgeRuntimeLibrary"(it) }
    if (includeInJar) dependency?.let { "include"(it) }
}

fun DependencyHandlerScope.minecraft(version: String) = "minecraft"("com.mojang:minecraft:$version")

@Suppress("UnstableApiUsage")
fun LoomGradleExtensionAPI.setupMappings(version: String): Dependency = layered {
    officialMojangMappings()
    val mappingsVer = when (version) {
        "1.21" -> "2024.07.28"
        "1.20.1" -> "2023.09.03"
        "1.19.2" -> "2022.11.27"
        else -> throw IllegalStateException("Unknown mappings for version $version!")
    }
    parchment("org.parchmentmc.data:parchment-$version:$mappingsVer")
}

fun DependencyHandlerScope.modImplementation(dependency: String) = "modImplementation"(dependency)

fun DependencyHandlerScope.setupLoader(loom: LoomGradleExtensionAPI, loader: String, version: String) {
    minecraft(version)
    "mappings"(loom.setupMappings(version))

    "compileOnly"("io.github.llamalad7:mixinextras-common:0.4.1")

    when (loader) {
        "fabric" -> {
            when (version) {
                "1.21" -> {
                    modImplementation("net.fabricmc:fabric-loader:0.15.11")
                    install("net.fabricmc.fabric-api:fabric-api:0.102.0+$version", isMod = true)
                    modImplementation("mods:sodium:0.6.0")
                    modImplementation("mods:iris:1.8.0")
                }

                "1.20.1" -> {
                    modImplementation("net.fabricmc:fabric-loader:0.15.11")
                    install("net.fabricmc.fabric-api:fabric-api:0.92.2+$version", isMod = true)
                    modImplementation("mods:sodium:0.5.11")
                    modImplementation("mods:iris:1.7.2")
                }

                "1.19.2" -> {
                    modImplementation("net.fabricmc:fabric-loader:0.15.11")
                    install("net.fabricmc.fabric-api:fabric-api:0.77.0+$version", isMod = true)
                    modImplementation("mods:sodium:0.4.4")
                    modImplementation("mods:iris:1.6.11")
                    modImplementation("curse.maven:spark-361579:4505310")
                    install("org.joml:joml:1.10.8")
                }

                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }
            install("io.github.classgraph:classgraph:4.8.173")
            install("io.github.llamalad7:mixinextras-fabric:0.4.1")
        }

        "forge" -> {
            when (version) {
                "1.21" -> "forge"("net.minecraftforge:forge:$version-51.0.8")
                "1.20.1" -> {
                    "forge"("net.minecraftforge:forge:$version-47.3.6")
                    "compileOnly"("mods:oculus-mc1.20.1:1.7.0")
                    "compileOnly"("mods:embeddium:0.3.31+mc1.20.1")
                }

                "1.19.2" -> {
                    install("org.joml:joml:1.10.8")
                    "forge"("net.minecraftforge:forge:$version-43.4.2")
                }

                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }
            "forgeRuntimeLibrary"("io.github.llamalad7:mixinextras-common:0.4.1")
            install("io.github.llamalad7:mixinextras-forge:0.4.1", isMod = true)
        }

        "neoforge" -> {
            when (version) {
                "1.21" -> "neoForge"("net.neoforged:neoforge:21.0.14-beta")
                else -> throw IllegalStateException("Unsupported $loader version $version!")
            }

            install("io.github.llamalad7:mixinextras-neoforge:0.4.1")
        }
    }
}
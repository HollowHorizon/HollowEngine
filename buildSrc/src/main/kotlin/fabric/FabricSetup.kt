package fabric

import common.DependencySetup
import install
import modImplementation
import org.gradle.kotlin.dsl.DependencyHandlerScope

object FabricSetup: DependencySetup {
    override fun DependencyHandlerScope.setup(minecraftVersion: String) {
        when (minecraftVersion) {
            "1.21.1" -> {
                modImplementation("net.fabricmc:fabric-loader:0.17.0")
                install("net.fabricmc.fabric-api:fabric-api:0.116.4+$minecraftVersion", isMod = true)
                modImplementation("mods:sodium-fabric:0.6.13+mc1.21.1")
                modImplementation("mods:iris-fabric:1.8.8+mc1.21.1")
            }

            "1.21" -> {
                modImplementation("net.fabricmc:fabric-loader:0.15.11")
                install("net.fabricmc.fabric-api:fabric-api:0.102.0+$minecraftVersion", isMod = true)
                modImplementation("mods:sodium:0.6.0")
                modImplementation("mods:iris:1.8.0")
            }

            "1.20.1" -> {
                modImplementation("net.fabricmc:fabric-loader:0.15.11")
                install("net.fabricmc.fabric-api:fabric-api:0.92.2+$minecraftVersion", isMod = true)
                modImplementation("mods:sodium:0.5.11")
                modImplementation("mods:iris:1.7.2")
            }

            "1.19.2" -> {
                modImplementation("net.fabricmc:fabric-loader:0.15.11")
                install("net.fabricmc.fabric-api:fabric-api:0.77.0+$minecraftVersion", isMod = true)
                modImplementation("mods:sodium:0.4.4")
                modImplementation("mods:iris:1.6.11")
                modImplementation("curse.maven:spark-361579:4505310")
                install("org.joml:joml:1.10.8")
            }

            else -> throw IllegalStateException("Unsupported Fabric version $minecraftVersion!")
        }
        install("io.github.classgraph:classgraph:4.8.173")
        install("io.github.llamalad7:mixinextras-fabric:0.4.1")
    }

    fun fabricLoader(minecraftVersion: String) = when(minecraftVersion) {
        "1.21.1" -> "0.17.0"
        else -> "0.15.11"
    }
    fun fabricApi(minecraftVersion: String) = when(minecraftVersion) {
        "1.21.1" -> "0.116.4+$minecraftVersion"
        "1.21" -> "0.102.0+$minecraftVersion"
        "1.20.1" -> "0.92.2+$minecraftVersion"
        "1.19.2" -> "0.77.0+$minecraftVersion"
        else -> error("Unsupported fabric api version for $minecraftVersion")
    }
}
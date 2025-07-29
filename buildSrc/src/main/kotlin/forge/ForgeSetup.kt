package forge

import common.DependencySetup
import install
import modImplementation
import org.gradle.kotlin.dsl.DependencyHandlerScope

object ForgeSetup: DependencySetup {
    override fun DependencyHandlerScope.setup(minecraftVersion: String) {
        when (minecraftVersion) {
            "1.21" -> "forge"("net.minecraftforge:forge:$minecraftVersion-51.0.8")
            "1.20.1" -> {
                "forge"("net.minecraftforge:forge:$minecraftVersion-47.4.3")
                "compileOnly"("mods:oculus-mc1.20.1:1.7.0")
                "compileOnly"("mods:embeddium:0.3.31+mc1.20.1")
            }

            "1.19.2" -> {
                install("org.joml:joml:1.10.8")
                "forge"("net.minecraftforge:forge:$minecraftVersion-43.4.2")
            }

            else -> throw IllegalStateException("Unsupported Forge version $minecraftVersion!")
        }
        install("io.github.llamalad7:mixinextras-forge:0.4.1")
    }

    fun forgeVersion(minecraftVersion: String) = when(minecraftVersion) {
        "1.21" -> "$minecraftVersion-51.0.8"
        "1.20.1" -> "$minecraftVersion-47.4.3"
        "1.19.2" -> "$minecraftVersion-43.4.2"
        else -> error("Unsupported forge version for $minecraftVersion")
    }
}
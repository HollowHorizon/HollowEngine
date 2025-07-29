package neoforge

import common.DependencySetup
import install
import org.gradle.kotlin.dsl.DependencyHandlerScope

object NeoForgeSetup: DependencySetup {
    override fun DependencyHandlerScope.setup(minecraftVersion: String) {
        when (minecraftVersion) {
            "1.21" -> "neoForge"("net.neoforged:neoforge:21.0.14-beta")
            else -> throw IllegalStateException("Unsupported NeoForge version $minecraftVersion!")
        }

        install("io.github.llamalad7:mixinextras-neoforge:0.4.1")
    }

    fun forgeVersion(minecraftVersion: String) = when(minecraftVersion) {
        "1.21" -> "$minecraftVersion-51.0.8"
        "1.20.1" -> "$minecraftVersion-47.4.3"
        "1.19.2" -> "$minecraftVersion-43.4.2"
        else -> error("Unsupported forge version for $minecraftVersion")
    }
}
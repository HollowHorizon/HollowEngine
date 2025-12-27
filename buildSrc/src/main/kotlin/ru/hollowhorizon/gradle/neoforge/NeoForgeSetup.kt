package ru.hollowhorizon.gradle.neoforge

import org.gradle.kotlin.dsl.DependencyHandlerScope
import ru.hollowhorizon.gradle.common.DependencySetup
import ru.hollowhorizon.gradle.install

object NeoForgeSetup: DependencySetup {
    override fun DependencyHandlerScope.setup(minecraftVersion: String) {
        when (minecraftVersion) {
            "1.21" -> "neoForge"("net.neoforged:neoforge:21.0.167")
            "1.21.1" -> {
                "neoForge"("net.neoforged:neoforge:21.1.197")
                "compileOnly"("mods:iris-neoforge:1.8.12+mc1.21.1")
                "compileOnly"("mods:sodium-neoforge:0.6.13+mc1.21.1")
            }
            else -> throw IllegalStateException("Unsupported NeoForge version $minecraftVersion!")
        }

        install("io.github.llamalad7:mixinextras-neoforge:0.4.1")
    }

    fun forgeVersion(minecraftVersion: String) = when(minecraftVersion) {
        "1.21" -> "$minecraftVersion-21.0.167"
        "1.21.1" -> "$minecraftVersion-21.1.216"
        else -> error("Unsupported forge version for $minecraftVersion")
    }
}
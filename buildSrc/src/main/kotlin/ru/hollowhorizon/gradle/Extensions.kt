package ru.hollowhorizon.gradle

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.named

var isForgelike = false

fun DependencyHandlerScope.install(path: String, includeInJar: Boolean = true, isMod: Boolean = false) {
    val dependency = if (isMod) modImplementation(path) else "implementation"(path) {
        isTransitive = false
    }

    dependency.takeIf { isForgelike && !isMod }?.let { "forgeRuntimeLibrary"(it) }
    if (includeInJar) dependency?.let { "include"(it) }
}

fun DependencyHandlerScope.minecraft(version: String) =
    "minecraft"("com.mojang:minecraft:$version")

@Suppress("UnstableApiUsage")
fun LoomGradleExtensionAPI.setupMappings(version: String): Dependency = layered {
    officialMojangMappings()
    val mappingsVer = when (version) {
        "1.21.1" -> "2024.11.17"
        "1.21" -> "2024.07.28"
        "1.20.1" -> "2023.09.03"
        "1.19.2" -> "2022.11.27"
        else -> throw IllegalStateException("Unknown mappings for version $version!")
    }
    parchment("org.parchmentmc.data:parchment-$version:$mappingsVer")
}

fun DependencyHandlerScope.modImplementation(dependency: String) =
    "modImplementation"(dependency)

val SourceSetContainer.main get() = named<SourceSet>("main")

val Project.minecraftVersion get() = name.substringBeforeLast('-')
val Project.modPlatform get() = name.substringAfterLast('-')

val StonecutterBuildExtension.modPlatform get() = current.project.substringAfterLast('-')
val StonecutterBuildExtension.minecraftVersion get() = current.project.substringBeforeLast('-')

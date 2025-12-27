package ru.hollowhorizon.gradle

import me.fallenbreath.yamlang.YamlangExtension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import ru.hollowhorizon.gradle.fabric.FabricModGenerator
import ru.hollowhorizon.gradle.forge.ForgeModGenerator
import ru.hollowhorizon.gradle.neoforge.NeoForgeModGenerator

object ResourcesSetup {
    fun setupResources(
        project: Project,
        modProject: ModProject,
        minecraftVersion: String,
        modPlatform: String
    ) {
        val sourceSets = project.extensions["sourceSets"] as SourceSetContainer
        val yamlang = project.extensions["yamlang"] as YamlangExtension

        sourceSets["main"].resources.srcDir(project.layout.buildDirectory.dir("generated/resources"))


        val generator = when(modPlatform) {
            "fabric" -> FabricModGenerator.generate(project, modProject)
            "forge" -> ForgeModGenerator.generate(project, modProject)
            "neoforge" -> NeoForgeModGenerator.generate(project, modProject)
            else -> null
        }?.apply {
            val generator = this
            project.tasks.named<Jar>("sourcesJar") {
                archiveClassifier.set("sources")
                from(sourceSets["main"].allSource)
                dependsOn(generator)
            }
        }

        project.tasks.named<ProcessResources>("processResources") {
            if(generator != null) dependsOn(generator)

            from(sourceSets.main.get().resources)
            when (modPlatform) {
                "forge" -> exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
                "neoforge" -> exclude("fabric.mod.json", "META-INF/mods.toml")
                "fabric" -> exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
            }

            exclude("architectury.common.marker")

            filesMatching(
                listOf(
                    "META-INF/mods.toml",
                    "fabric.mod.json",
                    "META-INF/neoforge.mods.toml",
                    "${modProject.modId}.mixins.json"
                )
            ) {
                expand(
                    mapOf(
                        "mod_version" to modProject.modVersion,
                        "mod_id" to modProject.modId,
                        "mod_name" to modProject.modName,
                        "license" to modProject.license,
                        "mc_version" to minecraftVersion
                    )
                )
            }
        }

        yamlang.apply {
            targetSourceSets.set(mutableListOf(sourceSets["main"]))
            inputDir.set("assets/${modProject.modId}/lang")
        }
    }
}
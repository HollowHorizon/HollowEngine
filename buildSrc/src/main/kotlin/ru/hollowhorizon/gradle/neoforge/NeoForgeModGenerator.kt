package ru.hollowhorizon.gradle.neoforge

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import ru.hollowhorizon.gradle.ModProject
import ru.hollowhorizon.gradle.minecraftVersion
import java.io.File

object NeoForgeModGenerator {
    fun generate(project: Project, modProject: ModProject) = project.tasks.register<Task>("generateFabricModJson") {
        val outputDir = project.layout.buildDirectory.dir("generated/resources/META-INF").get().asFile
        val outputFile = File(outputDir, "neoforge.mods.toml")

        val minecraftVersion = (project.extensions["stonecutter"] as StonecutterBuildExtension).minecraftVersion

        outputs.file(outputFile)

        doLast {
            val forgeMod = """
                modLoader = "javafml"
                loaderVersion = "[2,)"
                license = "${modProject.license}"
                [[mods]]
                modId = "${modProject.modId}"
                version = "${modProject.modVersion}"
                displayName = "${modProject.modName}"
                logoFile = "${modProject.modId}.png"
                credits = ""
                authors = "${modProject.authors.joinToString(", ")}"
                description = '''${modProject.description}'''
                [[mixins]]
                config = "${modProject.modId}.mixins.json"
                [[accessTransformers]]
                file="accesstransformer.cfg"
                [[dependencies.${modProject.modId}]]
                modId = "neoforge"
                type = "required"
                versionRange = "[${NeoForgeSetup.forgeVersion(minecraftVersion)},)"
                ordering = "NONE"
                side = "BOTH"
                [[dependencies.${modProject.modId}]]
                modId = "minecraft"
                type="required"
                versionRange = "[${(project.extensions["stonecutter"] as StonecutterBuildExtension).minecraftVersion},)"
                ordering = "NONE"
                side = "BOTH"
                
            """.trimIndent()

            val deps = modProject.dependencies.map { (modId, version) ->
                """
                    [[dependencies.${modProject.modId}]]
                    modId = "$modId"
                    type="required"
                    versionRange="[${version.removePrefix(">=")},)"
                    ordering = "NONE"
                    side = "BOTH"
                """.trimIndent()
            }.joinToString("\n")

            outputFile.writeText(forgeMod + deps)
        }
    }
}
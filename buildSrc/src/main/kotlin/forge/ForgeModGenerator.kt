package forge

import ModProject
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.controller.StonecutterControllerExtension
import kotlinx.serialization.json.Json
import minecraftVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.io.File

object ForgeModGenerator {
    fun generate(project: Project, modProject: ModProject) = project.tasks.register<Task>("generateFabricModJson") {
        val outputDir = project.layout.buildDirectory.dir("generated/resources/META-INF").get().asFile
        val outputFile = File(outputDir, "mods.toml")

        val minecraftVersion = (project.extensions["stonecutter"] as StonecutterBuildExtension).minecraftVersion

        outputs.file(outputFile)

        doLast {
            val forgeMod = """
                modLoader = "javafml"
                loaderVersion = "[43,)"
                license = "${modProject.license}"
                [[mods]]
                modId = "${modProject.modId}"
                version = "${modProject.modVersion}"
                displayName = "${modProject.modName}"
                logoFile = "${modProject.modId}.png"
                credits = ""
                authors = "${modProject.authors.joinToString(", ")}"
                description = '''${modProject.description}'''
                [[dependencies.${modProject.modId}]]
                modId = "forge"
                mandatory = true
                versionRange = "[${ForgeSetup.forgeVersion(minecraftVersion)},)"
                ordering = "NONE"
                side = "BOTH"
                [[dependencies.${modProject.modId}]]
                modId = "minecraft"
                mandatory = true
                versionRange = "[${(project.extensions["stonecutter"] as StonecutterBuildExtension).minecraftVersion},)"
                ordering = "NONE"
                side = "BOTH"
            """.trimIndent()


            val deps = modProject.dependencies.map { (modId, version) ->
                """
                    [[dependencies.${modProject.modId}]]
                    modId = "$modId"
                    mandatory = true
                    versionRange="[${version.removePrefix(">=")},)"
                    ordering = "NONE"
                    side = "BOTH"
                """.trimIndent()
            }.joinToString("\n")

            outputFile.writeText(forgeMod + deps)
        }
    }
}
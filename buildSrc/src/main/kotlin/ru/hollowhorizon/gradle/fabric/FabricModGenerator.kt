package ru.hollowhorizon.gradle.fabric

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.register
import ru.hollowhorizon.gradle.ModProject
import ru.hollowhorizon.gradle.minecraftVersion
import java.io.File


object FabricModGenerator {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun generate(project: Project, modProject: ModProject) = project.tasks.register<Task>("generateFabricModJson") {
        val outputDir = project.layout.buildDirectory.dir("generated/resources").get().asFile
        val outputFile = File(outputDir, "fabric.mod.json")

        val minecraftVersion = project.minecraftVersion

        outputs.file(outputFile)

        doLast {
            val file = FabricMod(
                modId = modProject.modId,
                modName = modProject.modName,
                modVersion = modProject.modVersion,
                description = modProject.description,
                authors = modProject.authors,
                license = modProject.license,
                entrypoints = modProject.entryPoints,
                mixins = modProject.mixinConfigs.ifEmpty { listOf("${modProject.modId}.mixins.json") },
                depends = modProject.dependencies + mapOf(
                    "fabricloader" to FabricSetup.fabricLoader(minecraftVersion).greaterEqual(),
                    "fabric-api" to FabricSetup.fabricApi(minecraftVersion).greaterEqual(),
                    "minecraft" to minecraftVersion,
                    "java" to ">=17"
                )
            )

            json.encodeToStream(file, outputFile.outputStream())
        }
    }

    private fun String.greaterEqual() = ">=$this"
}

@Serializable
data class FabricMod(
    val schemaVersion: Int = 1,
    @SerialName("id")
    val modId: String,
    @SerialName("version")
    val modVersion: String,
    @SerialName("name")
    val modName: String,
    val description: String,
    val authors: List<String> = emptyList(),
    val contact: Map<String, String> = mapOf(),
    val license: String,
    val icon: String = "$modId.png",
    val environment: String = "*",
    val entrypoints: Map<String, List<String>>,
    val accessWidener: String = "$modId.accesswidener",
    val mixins: List<String>,
    val depends: Map<String, String>,
    val suggests: Map<String, String> = mapOf(),
)

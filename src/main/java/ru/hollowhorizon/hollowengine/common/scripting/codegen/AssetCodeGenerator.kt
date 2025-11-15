package ru.hollowhorizon.hollowengine.common.scripting.codegen

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.scripting.codegen.asset.AssetGenerator
import ru.hollowhorizon.hollowengine.common.scripting.codegen.asset.FileAssetGenerator
import ru.hollowhorizon.hollowengine.common.scripting.codegen.asset.ResourceFile
import ru.hollowhorizon.hollowengine.common.scripting.codegen.asset.ResourceFolder
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.walk
import java.io.File

object AssetCodeGenerator {
    private val generators = mutableMapOf<String, AssetGenerator<*>>()
    private val defaultGenerator = FileAssetGenerator()


    fun <T> registerGenerator(extension: String, generator: AssetGenerator<T>) {
        generators[extension] = generator
    }

    fun generateAssetsClass(manager: ResourceManager, outputDir: String? = null): String {
        val rootFolder = ResourceFolder("Assets")

        listOf("models", "textures").forEach { type ->

            // Получаем все ресурсы в namespace
            val allResources = manager.walk(type)

            // Строим дерево папок
            allResources.forEach { location ->
                val path = location.path
                val parts = path.split('/')

                var currentFolder = rootFolder.getOrCreateFolder(location.namespace)

                // Создаем папки для всех частей пути, кроме последней
                for (i in 0 until parts.size - 1) {
                    currentFolder = currentFolder.getOrCreateFolder(parts[i])
                }

                // Последняя часть - файл
                val fileName = parts.last()
                val extension = fileName.substringAfterLast('.', "")
                val baseName = fileName.substringBeforeLast('.')

                val generator = generators[extension.lowercase()] ?: defaultGenerator
                val fileNode = ResourceFile(baseName, location, generator)

                currentFolder.addFile(baseName, fileNode)
            }
        }


        val generatedCode = """
            |package generated.assets
            |
            |import ru.hollowhorizon.hollowengine.client.models.internal.animations.*
            |import ru.hollowhorizon.hollowengine.common.scripting.codegen.asset.*
            |
            |${rootFolder.generateCode(manager, 0)}
        """.trimMargin()

        outputDir?.let { dir ->
            val outputFile = File(dir, "Assets.kt")
            outputFile.parentFile.mkdirs()
            outputFile.writeText(generatedCode)
        }

        HollowEngine.LOGGER.info("Generating ${rootFolder.name}")
        HollowEngine.LOGGER.info(generatedCode)

        return generatedCode
    }

    private fun findResources(manager: ResourceManager): Map<String, List<ResourceLocation>> {
        val resources = mutableMapOf<String, MutableList<ResourceLocation>>()

        val assetTypes = listOf("models", "textures", "sounds", "scripts", "shaders")

        assetTypes.forEach { type ->
            resources[type] = manager.walk(type).toMutableList()
        }

        return resources
    }

    private fun buildAssetsClass(resources: Map<String, List<ResourceLocation>>, manager: ResourceManager): String {
        val imports = """            
            import ru.hollowhorizon.hollowengine.client.models.internal.animations.*
            import ru.hollowhorizon.hollowengine.common.scripting.codegen.assets.*
        """.trimIndent()

        val objects = resources.map { (type, locations) ->
            buildObjectForType(type, locations, manager)
        }.joinToString("\n\n")

        return """
            $imports
            
            object Assets {
            $objects
            }
        """.trimIndent()
    }

    private fun buildObjectForType(type: String, locations: List<ResourceLocation>, manager: ResourceManager): String {
        val typeName = type.replaceFirstChar { it.uppercase() }
        val packages = HashMap<String, MutableList<ResourceLocation>>()
        locations.forEach { location ->
            packages.getOrPut(location.namespace) { arrayListOf() }.add(location)
        }

        return buildString {
            packages.forEach { (name, locations) ->
                appendLine("    object ${name.replaceFirstChar { it.uppercase() }} {")
                val fields = locations.joinToString("\n    ") { location ->
                    generateAssetField(location, manager)
                }
                appendLine("    $fields")
                appendLine("    }")
            }
        }
    }

    private fun generateAssetField(location: ResourceLocation, manager: ResourceManager): String {
        val extension = location.path.substringAfterLast('.')

        // Ищем подходящий генератор
        val generator = generators.values.find { generator ->
            generator.fileExtensions.any { it == "*" || it.equals(extension, true) }
        } ?: defaultGenerator

        return try {
            val asset = generator.generate(manager, location)
            generator.generateCode(location, JavaHacks.forceCast(asset))
        } catch (e: Exception) {
            // В случае ошибки генерируем базовый файл
            "val ${generateFieldName(location)} = AssetFile(\"$location\") // Error: ${e.message}"
        }
    }
}


fun generateFieldName(location: ResourceLocation): String {
    return location.path
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .sanitizeFieldName()
        .uppercase()
}

fun String.sanitizeFieldName(): String {
    return this.replace(Regex("[^a-zA-Z0-9]"), "_")
        .replaceFirstChar { it.uppercase() }
}
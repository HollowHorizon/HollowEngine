package ru.hollowhorizon.hollowengine.common.scripting.codegen.asset

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hollowengine.common.scripting.codegen.sanitizeFieldName
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

data class ResourceFolder(
    override val name: String,
    val children: MutableMap<String, ResourceNode> = mutableMapOf()
) : ResourceNode() {
    override fun generateCode(manager: ResourceManager, indent: Int): String {
        val currentIndent = " ".repeat(indent)

        if (children.isEmpty()) return ""

        val childCode = children.values.joinToString("\n\n") { child ->
            child.generateCode(manager, indent + 4)
        }

        val folderName = name.sanitizeFieldName().replaceFirstChar { it.uppercase() }

        return """${currentIndent}object $folderName {
            |$childCode
            |$currentIndent}""".trimMargin()
    }

    fun addFile(name: String, node: ResourceFile) {
        children[name] = node
    }

    fun getOrCreateFolder(name: String): ResourceFolder {
        return children.getOrPut(name) { ResourceFolder(name) } as ResourceFolder
    }
}

data class ResourceFile(
    override val name: String,
    val location: ResourceLocation,
    val generator: AssetGenerator<*>
) : ResourceNode() {
    override fun generateCode(manager: ResourceManager, indent: Int): String {
        val currentIndent = " ".repeat(indent)
        return try {
            val asset = generator.generate(manager, location)
            currentIndent + generator.generateCode(location, JavaHacks.forceCast(asset))
        } catch (e: Exception) {
            "$currentIndent// Error generating $location: ${e.message}"
        }
    }
}
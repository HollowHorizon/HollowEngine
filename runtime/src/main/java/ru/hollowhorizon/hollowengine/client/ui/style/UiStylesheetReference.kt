package ru.hollowhorizon.hollowengine.client.ui.style

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.StyleImportModifier
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.flattenModifiers
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun interface HssResourceLoader {
    fun load(location: String): CompiledHss

    fun version(location: String): Long = 0L
}

sealed interface UiStylesheetReference {
    fun resolve(): CompiledHss

    fun revision(): Long

    data class Compiled(private val stylesheet: CompiledHss) : UiStylesheetReference {
        override fun resolve(): CompiledHss = stylesheet

        override fun revision(): Long = 0L
    }

    data class Resource(
        private val location: String,
        private val loader: HssResourceLoader,
    ) : UiStylesheetReference {
        private var cachedVersion: Long? = null
        private var cachedStylesheet: CompiledHss? = null

        override fun resolve(): CompiledHss {
            val version = revision()
            val stylesheet = cachedStylesheet
            if (stylesheet != null && cachedVersion == version) return stylesheet
            return loader.load(location).also {
                cachedStylesheet = it
                cachedVersion = version
            }
        }

        override fun revision(): Long = loader.version(location)
    }
}

object MinecraftHssResourceLoader : HssResourceLoader {
    private val locations = ConcurrentHashMap<String, ResourceLocation>()

    override fun load(location: String): CompiledHss {
        return runCatching {
            compileHss(HollowUiResourceAccess.readText(resourceLocation(location)))
        }.getOrElse {
            HollowEngine.LOGGER.error("Error while loading $location", it)
            CompiledHss(emptyList())
        }
    }

    override fun version(location: String): Long {
        return HollowUiResourceAccess.version(resourceLocation(location))
    }

    private fun resourceLocation(location: String): ResourceLocation =
        locations.computeIfAbsent(location, ResourceLocation::parse)
}

fun UiNode.stylesheetRevision(): Long {
    var revision = 1L
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        for (modifier in node.modifiers.flattenModifiers()) {
            if (modifier is StyleImportModifier) {
                revision = revision * 31L + modifier.reference.revision()
            }
        }
        node.children.forEach(stack::add)
    }
    return revision
}

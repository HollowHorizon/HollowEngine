package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.UiPreviewState
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss

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

    class Resource(
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
    override fun load(location: String): CompiledHss {
        return runCatching {
            compileHss(HollowUiResourceAccess.readText(ResourceLocation.parse(location)))
        }.getOrElse {
            HollowEngine.LOGGER.error("Error while loading $location", it)
            UiPreviewState.errorText.set("Error while loading $location: ${it.message}")
            CompiledHss(emptyList())
        }
    }

    override fun version(location: String): Long {
        return HollowUiResourceAccess.version(ResourceLocation.parse(location))
    }
}

fun UiNode.stylesheetRevision(): Long {
    var revision = 1L
    visitStylesheetReferences { reference ->
        revision = revision * 31L + reference.revision()
    }
    return revision
}

private fun UiNode.visitStylesheetReferences(visitor: (UiStylesheetReference) -> Unit) {
    modifiers.flattenModifiers()
        .filterIsInstance<StyleImportModifier>()
        .forEach { visitor(it.reference) }
    children.forEach { it.visitStylesheetReferences(visitor) }
}

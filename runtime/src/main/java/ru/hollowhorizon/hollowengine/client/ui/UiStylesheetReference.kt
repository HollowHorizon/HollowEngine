package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss

fun interface HssResourceLoader {
    fun load(location: String): CompiledHss
}

sealed interface UiStylesheetReference {
    fun resolve(): CompiledHss

    data class Compiled(private val stylesheet: CompiledHss) : UiStylesheetReference {
        override fun resolve(): CompiledHss = stylesheet
    }

    class Resource(
        private val location: String,
        private val loader: HssResourceLoader,
    ) : UiStylesheetReference {
        override fun resolve(): CompiledHss = loader.load(location)
    }
}

object MinecraftHssResourceLoader : HssResourceLoader {
    override fun load(location: String): CompiledHss {
        return compileHss(HollowUiResourceAccess.readText(ResourceLocation.parse(location)))
    }
}

package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import java.io.InputStreamReader

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
        private val stylesheet: CompiledHss by lazy { loader.load(location) }

        override fun resolve(): CompiledHss = stylesheet
    }
}

object MinecraftHssResourceLoader : HssResourceLoader {
    override fun load(location: String): CompiledHss {
        val resource = ResourceLocation.parse(location)
        return HollowJavaUtils.getResource(resource).use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                compileHss(reader.readText())
            }
        }
    }
}

package ru.hollowhorizon.hollowengine.client.kool.minecraft

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.Image
import de.fabmax.kool.modules.ui2.ImageScope
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.image
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture2d
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.LinkedHashMap

private const val MaxCachedImageLocations = 512

object ImageManager : ResourceManagerReloadListener {
    private val IMAGES = Object2ObjectOpenHashMap<ResourceLocation, Texture2d>()
    private val locations = object : LinkedHashMap<String, ResourceLocation>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceLocation>?): Boolean =
            size > MaxCachedImageLocations
    }

    fun location(value: String): ResourceLocation = locations.getOrPut(value) { value.rl }

    fun load(location: ResourceLocation, mode: SamplerMode): Texture2d = IMAGES.getOrPut(location) {
        Texture2d(
            mipMapping = MipMapping.Off, samplerSettings = SamplerSettings().let {
                if (mode == SamplerMode.NEAREST && !location.path.endsWith(".svg")) it.nearest() else it.linear()
            }
        ) {
            Assets.loadImage2d(location.toString()).getOrThrow()
        }
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        IMAGES.forEach { location, image ->
            image.uploadLazy {
                Assets.loadImage2d(location.toString()).getOrThrow()
            }
        }
    }
}

fun UiScope.Image(location: ResourceLocation, mode: SamplerMode = SamplerMode.NEAREST, block: ImageScope.() -> Unit = {}) =
    Image {
        modifier.image(ImageManager.load(location, mode))

        block()
    }

fun UiScope.Image(location: String, mode: SamplerMode = SamplerMode.NEAREST, block: ImageScope.() -> Unit = {}) =
    Image(ImageManager.location(location), mode, block)

enum class SamplerMode {
    NEAREST, LINEAR
}

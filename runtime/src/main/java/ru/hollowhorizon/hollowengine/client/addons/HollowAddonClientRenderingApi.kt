package ru.hollowhorizon.hollowengine.client.addons

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtensionPoint
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtensions
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonRegistration
import ru.hollowhorizon.hollowengine.common.ui.hud.HudLayerRegistry

object HollowAddonClientExtensionPoints {
    val HUD_LAYERS = HollowAddonExtensionPoint(
        "hollowengine:minecraft/hud-layers",
        HollowAddonHudLayer::class,
    )
    val HUD_OVERLAYS = HollowAddonExtensionPoint(
        "hollowengine:minecraft/hud-overlays",
        HollowAddonHudOverlay::class,
    )
}

enum class HollowAddonHudPlacement {
    BEFORE,
    AFTER,
}

data class HollowAddonHudRenderContext(
    val window: Window,
    val graphics: GuiGraphics,
    val partialTick: Float,
)

class HollowAddonHudLayer(
    val id: ResourceLocation,
    val anchor: ResourceLocation,
    val placement: HollowAddonHudPlacement = HollowAddonHudPlacement.AFTER,
    val render: (HollowAddonHudRenderContext) -> Unit,
)

class HollowAddonHudOverlay(
    val id: ResourceLocation,
    val render: (HollowAddonHudRenderContext) -> Unit,
)

fun HollowAddonExtensions.registerHudLayer(
    layer: HollowAddonHudLayer,
    priority: Int = 0,
): HollowAddonRegistration = register(
    HollowAddonClientExtensionPoints.HUD_LAYERS,
    layer.id.toString(),
    layer,
    priority,
)

fun HollowAddonExtensions.registerHudOverlay(
    overlay: HollowAddonHudOverlay,
    priority: Int = 0,
): HollowAddonRegistration = register(
    HollowAddonClientExtensionPoints.HUD_OVERLAYS,
    overlay.id.toString(),
    overlay,
    priority,
)

internal object HollowAddonClientRendering {
    fun renderLayers(
        anchor: ResourceLocation,
        placement: HollowAddonHudPlacement,
        context: HollowAddonHudRenderContext,
    ) {
        HollowAddonClientExtensionPoints.HUD_LAYERS.extensions()
            .filter { extension ->
                val layer = extension.value
                layer.anchor == anchor && layer.placement == placement && !HudLayerRegistry.isHidden(layer.id)
            }
            .forEach { extension ->
                runCatching { extension.invoke { layer -> layer.render(context) } }
                    .onFailure { failure -> reportFailure(extension.qualifiedId, failure) }
            }
    }

    fun renderOverlays(context: HollowAddonHudRenderContext) {
        HollowAddonClientExtensionPoints.HUD_OVERLAYS.extensions()
            .filterNot { extension -> HudLayerRegistry.isHidden(extension.value.id) }
            .forEach { extension ->
                runCatching { extension.invoke { overlay -> overlay.render(context) } }
                    .onFailure { failure -> reportFailure(extension.qualifiedId, failure) }
            }
    }

    private fun reportFailure(id: String, failure: Throwable) {
        HollowEngine.LOGGER.error("Addon HUD extension '{}' failed", id, failure)
    }
}

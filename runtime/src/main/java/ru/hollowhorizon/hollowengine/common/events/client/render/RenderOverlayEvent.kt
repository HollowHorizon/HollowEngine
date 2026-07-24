package ru.hollowhorizon.hollowengine.common.events.client.render

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

/**
 * Fired around each vanilla HUD layer. The layer is identified by a [ResourceLocation] (see
 * [ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers]) rather than an enum constant, so
 * vanilla layers, scripted layers and addon layers all live in one namespace and can anchor to or
 * hide each other uniformly.
 */
abstract class RenderOverlayEvent protected constructor(
    val window: Window,
    val guiGraphics: GuiGraphics,
    val partialTick: Float,
    val layer: ResourceLocation,
) : ClientEvent {
    class Pre(
        window: Window,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        layer: ResourceLocation,
    ) : RenderOverlayEvent(window, guiGraphics, partialTick, layer), Cancellable {
        companion object : EventHandler<Pre>()

        override var isCanceled: Boolean = false
    }

    class Post(
        window: Window,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        layer: ResourceLocation,
    ) : RenderOverlayEvent(window, guiGraphics, partialTick, layer) {
        companion object : EventHandler<Post>()
    }
}

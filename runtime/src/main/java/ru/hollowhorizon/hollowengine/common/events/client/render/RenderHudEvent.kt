package ru.hollowhorizon.hollowengine.common.events.client.render

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.gui.GuiGraphics
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

/**
 * Fired once after the whole HUD is drawn, unlike [RenderOverlayEvent] which fires around each layer.
 */
class RenderHudEvent(
    val window: Window,
    val guiGraphics: GuiGraphics,
    val partialTick: Float,
) : ClientEvent {
    companion object : EventHandler<RenderHudEvent>()
}

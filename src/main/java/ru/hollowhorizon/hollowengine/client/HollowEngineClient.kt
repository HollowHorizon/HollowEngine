package ru.hollowhorizon.hollowengine.client

import ru.hollowhorizon.hc.client.kool.gl.render
import ru.hollowhorizon.hc.common.events.ClientOnly
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hc.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus

@ClientOnly
object HollowEngineClient {
    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.HOTBAR) return
        CompilationStatus.overlay.render()
    }
}
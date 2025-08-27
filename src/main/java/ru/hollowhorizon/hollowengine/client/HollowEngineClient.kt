package ru.hollowhorizon.hollowengine.client

import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus

@ClientOnly
object HollowEngineClient {
    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.HOTBAR) return
        CompilationStatus.overlay.render()
    }
}
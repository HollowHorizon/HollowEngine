package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.systems.RenderSystem
import ru.hollowhorizon.hc.api.Init
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.client.gui.overlay.BetaWarning
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus

@ClientOnly
object HollowEngineClient {
    init {
        RenderSystem.recordRenderCall {
            KoolManager.context.addScene(CompilationStatus.overlay)
            KoolManager.context.addScene(BetaWarning.overlay)
        }
    }
}
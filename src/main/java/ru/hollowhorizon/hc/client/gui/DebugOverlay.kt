package ru.hollowhorizon.hc.client.gui

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.common.capabilities.SyncableMapImpl

object DebugOverlay {
    val debugText = SyncableMapImpl.create<String, String>(HashMap()) {
        surface.triggerUpdate()
    }
    private val debugScene = Scene().apply {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare

        surface = addPanelSurface(sizes = Sizes.medium(normalText = MsdfFont(KoolManager.MONOCRAFT))) {
            modifier.size(FitContent, FitContent)
                .align(AlignmentX.End, AlignmentY.Top)

            debugText.forEach { (key, value) ->
                Text("${key}: $value") {}
            }
        }

        onUpdate {
            isVisible = debugText.isNotEmpty()
        }
    }
    private var surface: UiSurface

    internal fun init() {
        if (HollowCore.config.debugMode) KoolManager.context.addScene(debugScene)
    }
}
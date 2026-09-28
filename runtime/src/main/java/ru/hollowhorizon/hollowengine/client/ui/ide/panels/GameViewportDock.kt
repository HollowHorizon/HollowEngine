package ru.hollowhorizon.hollowengine.client.ui.ide.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.GameViewportNodeId
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeGameViewport
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.utils.lang

internal object GameViewportLang {
    private const val ROOT = "hollowengine.gui.ide.windows.game_viewport"

    const val DETACHED = "$ROOT.detached"
}

/**
 * The game, drawn as panel of editor. Frame is rendered straight into a target
 * cut down to this panel, so what it shows is the game at its own resolution rather than a
 * shrunken copy of the window.
 */
@Composable
fun GameViewportDock(active: Boolean = false, attached: Boolean = true) {
    val placement = remember { PanelPlacement() }
    DisposableEffect(attached) {
        val bounds = placement.bounds
        if (attached && bounds != null) HollowIdeGameViewport.report(placement, bounds)
        if (!attached) HollowIdeGameViewport.release(placement)
        onDispose { HollowIdeGameViewport.release(placement) }
    }
    Box(
        id = GameViewportNodeId,
        tags = listOfNotNull("game-viewport", "active".takeIf { active && attached }),
        modifier = Modifier.input(hoverable = true, clickable = true)
            .onPlaced {
                placement.bounds = it
                if (attached) HollowIdeGameViewport.report(placement, it)
            }
            .drawBehind(key = "game-viewport-$attached") {
                if (!attached) return@drawBehind
                val rect = HollowIdeGameViewport.imageRect(UiRect(0f, 0f, size.width, size.height)) ?: return@drawBehind
                drawTexture(
                    rect = rect,
                    texture = { Minecraft.getInstance().mainRenderTarget?.colorTextureId ?: 0 },
                    flipY = true,
                    opaque = true,
                )
            },
    ) {
        if (!attached) {
            Text(
                GameViewportLang.DETACHED.lang,
                tags = listOf("game-viewport-hint"),
                modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER).padding(16.px),
            )
        }
    }
}

private class PanelPlacement {
    var bounds: UiRect? = null
}

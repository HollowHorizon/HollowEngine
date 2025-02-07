package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlin.math.max
import kotlin.math.min

fun UiScope.hoverListener() = remember(false).apply {
    modifier.onEnter { set(true) }.onEnter { set(false) }
}

class TabRenderer(private val bgColor: Color, private val primary: Color) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            val lt = max(leftPx, clipLeftPx)
            val rt = min(rightPx, clipRightPx)
            val tp = max(topPx, clipTopPx)
            val bt = min(bottomPx, clipBottomPx)

            val width = rt - lt
            val height = bt - tp

            node.getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                rect(lt, tp, width, height * 0.9f, clipBoundsPx, bgColor)
                rect(lt, tp+ height * 0.9f, width, height * 0.1f, clipBoundsPx, primary)
            }
        }
    }

}
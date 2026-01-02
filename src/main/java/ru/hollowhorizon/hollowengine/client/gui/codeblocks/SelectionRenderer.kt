package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiScale
import de.fabmax.kool.util.Color
import kotlin.math.abs
import kotlin.math.min

class SelectionRenderer(val controller: BlockController, val editorScale: Float) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) = with(node) {
        if (!controller.isSelecting.value) return@with

        val startX = controller.selectionStart.x * editorScale
        val startY = controller.selectionStart.y * editorScale
        val currX = controller.selectionCurr.x * editorScale
        val currY = controller.selectionCurr.y * editorScale

        val scrollX = controller.scrollState.xScrollDp.use() * UiScale.measuredScale
        val scrollY = controller.scrollState.yScrollDp.use() * UiScale.measuredScale

        val x = min(startX, currX) + leftPx + paddingStartPx - scrollX
        val y = min(startY, currY) + topPx + paddingTopPx - scrollY
        val w = abs(startX - currX)
        val h = abs(startY - currY)

        node.getUiPrimitives(BlockEditor.Z_LAYER_SCROLLBAR).apply {
            val borderWidth = 1.dp.px

            // TODO: Перенести цвета куда-то ещё
            var color = Color("3399FF").withAlpha(0.3f)
            rect(x, y, w, h, clipBoundsPx, color)

            color = Color("3399FF").withAlpha(0.8f)
            rect(x, y, w, borderWidth, clipBoundsPx, color) // Top
            rect(x, y + h - borderWidth, w, borderWidth, clipBoundsPx, color) // Bottom
            rect(x, y, borderWidth, h, clipBoundsPx, color) // Left
            rect(x + w - borderWidth, y, borderWidth, h, clipBoundsPx, color) // Right
        }
    }
}
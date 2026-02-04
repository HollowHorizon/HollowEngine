package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color

class GridBackground(
    val sectionSize: Dp,
    val currentZoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val lineWidth: Dp,
    val lineColor: Color,
) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.Companion.LAYER_BACKGROUND).apply {
                val baseCellSize = sectionSize.px * currentZoom
                if (baseCellSize <= 0) return

                val minGridStepPx = 20f
                var effectiveCellSize = baseCellSize

                while (effectiveCellSize < minGridStepPx) effectiveCellSize *= 2
                while (effectiveCellSize > minGridStepPx * 4f) effectiveCellSize /= 2

                val lineThicknessPx = lineWidth.px.coerceAtLeast(1f)
                val halfThickness = lineThicknessPx / 2f

                val gridOriginX = (widthPx / 2f) + (offsetX * currentZoom)
                val gridOriginY = (heightPx / 2f) + (offsetY * currentZoom)

                val firstLineX = kotlin.math.floor((0f - gridOriginX) / effectiveCellSize).toInt()
                val lastLineX = kotlin.math.ceil((widthPx - gridOriginX) / effectiveCellSize).toInt()

                val firstLineY = kotlin.math.floor((0f - gridOriginY) / effectiveCellSize).toInt()
                val lastLineY = kotlin.math.ceil((heightPx - gridOriginY) / effectiveCellSize).toInt()

                for (i in firstLineX..lastLineX) {
                    val x = gridOriginX + (i * effectiveCellSize)
                    rect(leftPx + x - halfThickness, topPx, lineThicknessPx, heightPx, clipBoundsPx, lineColor)
                }

                for (i in firstLineY..lastLineY) {
                    val y = gridOriginY + (i * effectiveCellSize)
                    rect(leftPx, topPx + y - halfThickness, widthPx, lineThicknessPx, clipBoundsPx, lineColor)
                }
            }
        }
    }
}
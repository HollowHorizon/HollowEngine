package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

class GridBackground(
    val sectionSize: Dp,
    val currentZoom: Float,
    val scrollState: ScrollState,
    val lineWidth: Dp,
    val lineColor: Color,
) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.Companion.LAYER_BACKGROUND).apply {
                val minGridStepPx = 20f

                val baseCellSize = sectionSize.px * currentZoom

                var stepMultiplier = 1
                var effectiveCellSize = baseCellSize

                if (baseCellSize > 0) {
                    while (effectiveCellSize < minGridStepPx) {
                        stepMultiplier *= 2
                        effectiveCellSize = baseCellSize * stepMultiplier
                    }
                } else {
                    return
                }

                val lineThicknessPx = (lineWidth.px * currentZoom).coerceAtLeast(1f)

                val paddingOffset = Dimensions.PaddingLarge.px * currentZoom

                var startX = (paddingOffset - scrollState.xScrollDp.use() * UiScale.measuredScale) % effectiveCellSize
                var startY = (paddingOffset - scrollState.yScrollDp.use() * UiScale.measuredScale) % effectiveCellSize

                if (startX > 0) startX -= effectiveCellSize
                if (startY > 0) startY -= effectiveCellSize

                var x = startX
                while (x < widthPx) {
                    rect(
                        leftPx + x,
                        topPx,
                        lineThicknessPx,
                        heightPx,
                        clipBoundsPx,
                        lineColor
                    )
                    x += effectiveCellSize
                }

                var y = startY
                while (y < heightPx) {
                    rect(
                        leftPx,
                        topPx + y,
                        widthPx,
                        lineThicknessPx,
                        clipBoundsPx,
                        lineColor
                    )
                    y += effectiveCellSize
                }
            }
        }
    }
}
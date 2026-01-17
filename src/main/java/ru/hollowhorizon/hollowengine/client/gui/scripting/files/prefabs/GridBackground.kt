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
                val cellSize = sectionSize.px * currentZoom
                val lineThicknessPx = lineWidth.px * currentZoom

                val paddingOffset = Dimensions.PaddingLarge.px * currentZoom

                var startX = (paddingOffset - scrollState.xScrollDp.use() * UiScale.measuredScale) % cellSize
                var startY = (paddingOffset - scrollState.yScrollDp.use() * UiScale.measuredScale) % cellSize

                if (startX > 0) startX -= cellSize
                if (startY > 0) startY -= cellSize

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
                    x += cellSize
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
                    y += cellSize
                }
            }
        }
    }
}
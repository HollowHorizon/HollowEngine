package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

class GridBackground(
    val sectionSize: Dp,
    val currentZoom: Float,
    val scrollX: Float,
    val scrollY: Float,
    val lineWidth: Dp,
) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.Companion.LAYER_BACKGROUND).apply {
                val cellSize = sectionSize.px * currentZoom
                val lineThicknessPx = lineWidth.px * currentZoom

                val paddingOffset = Dimensions.PaddingLarge.px * currentZoom

                var startX = (paddingOffset + scrollX) % cellSize
                var startY = (paddingOffset + scrollY) % cellSize

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
                        ColorTheme.UI.BackgroundElements.withAlpha(0.65f)
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
                        ColorTheme.UI.BackgroundElements.withAlpha(0.65f)
                    )
                    y += cellSize
                }
            }
        }
    }
}
package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class BlockGridBackground(val editor: BlockEditor, val dotSize: Dp, val sectionSize: Dp): UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.Companion.LAYER_BACKGROUND).apply {
                val currentZoom = editor.scale

                val scrollX = editor.controller.scrollState.xScrollDp.use() * UiScale.measuredScale
                val scrollY = editor.controller.scrollState.yScrollDp.use() * UiScale.measuredScale

                val cellSize = sectionSize.px * currentZoom
                val dotPx = dotSize.px * currentZoom
                if (!cellSize.isFinite() || !dotPx.isFinite() || cellSize <= 0f || dotPx <= 0f) return@apply

                val paddingOffset = Dimensions.PaddingLarge.px * currentZoom
                if (!paddingOffset.isFinite()) return@apply

                val visibleDotCount = max(widthPx / cellSize, 0f) * max(heightPx / cellSize, 0f)
                val stride = max(
                    ceil(MIN_DOT_SPACING_PX / cellSize).toInt(),
                    ceil(sqrt(visibleDotCount / MAX_VISIBLE_DOTS)).toInt(),
                ).coerceAtLeast(1)
                val renderCellSize = cellSize * stride

                var startX = (paddingOffset - scrollX) % renderCellSize
                var startY = (paddingOffset - scrollY) % renderCellSize

                if (startX > 0) startX -= renderCellSize
                if (startY > 0) startY -= renderCellSize

                var x = startX
                while (x < widthPx) {
                    var y = startY
                    while (y < heightPx) {
                        rect(
                            leftPx + x, topPx + y,
                            dotPx, dotPx,
                            clipBoundsPx, ColorTheme.UI.BackgroundElements
                        )
                        y += renderCellSize
                    }
                    x += renderCellSize
                }
            }
        }
    }

    companion object {
        private const val MIN_DOT_SPACING_PX = 6f
        private const val MAX_VISIBLE_DOTS = 12_000f
    }
}

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

class BlockGridBackground(val editor: BlockEditor, val dotSize: Dp, val sectionSize: Dp): UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                val currentZoom = editor.scale

                val scrollX = editor.controller.scrollState.xScrollDp.use() * UiScale.measuredScale
                val scrollY = editor.controller.scrollState.yScrollDp.use() * UiScale.measuredScale

                val cellSize = sectionSize.px * currentZoom
                val dotPx = dotSize.px * currentZoom

                val paddingOffset = Dimensions.PaddingLarge.px * currentZoom

                var startX = (paddingOffset - scrollX) % cellSize
                var startY = (paddingOffset - scrollY) % cellSize

                if (startX > 0) startX -= cellSize
                if (startY > 0) startY -= cellSize

                var x = startX
                while (x < widthPx) {
                    var y = startY
                    while (y < heightPx) {
                        rect(
                            leftPx + x, topPx + y,
                            dotPx, dotPx,
                            clipBoundsPx, ColorTheme.UI.BackgroundElements
                        )
                        y += cellSize
                    }
                    x += cellSize
                }
            }
        }
    }
}

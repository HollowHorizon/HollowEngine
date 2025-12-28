package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme

class BlockGridBackground(val editor: BlockEditor, val dotSize: Dp, val sectionSize: Dp): UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                val scrollX = editor.controller.scrollState.xScrollDp.use() * UiScale.measuredScale
                val scrollY = editor.controller.scrollState.yScrollDp.use() * UiScale.measuredScale

                val cellSize = sectionSize.px
                val dotPx = dotSize.px

                val startX = -(scrollX % cellSize)
                val startY = -(scrollY % cellSize)

                val finalStartX = if (startX > 0) startX - cellSize else startX
                val finalStartY = if (startY > 0) startY - cellSize else startY

                var x = finalStartX
                while (x < widthPx) {
                    var y = finalStartY
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

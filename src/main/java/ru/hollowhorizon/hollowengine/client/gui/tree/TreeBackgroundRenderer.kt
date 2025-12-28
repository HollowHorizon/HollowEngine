package ru.hollowhorizon.hollowengine.client.gui.tree

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.modules.ui2.UiVertexLayout
import de.fabmax.kool.scene.geometry.MeshBuilder
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import kotlin.math.pow

class TreeBackgroundRenderer(val isExpanded: Boolean) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(ColorTheme.UI.BackgroundAccent) {
                val lineWidth = Dimensions.PaddingSmall.px * 0.75f

                val startX = widthPx / 2f
                val startY = 0f

                val endX = widthPx
                val endY = heightPx / 2f

                val controlX = widthPx / 2f
                val controlY = heightPx / 2f

                line(Vec2f(startX, startY), Vec2f(startX, heightPx), lineWidth)

                if (isExpanded) quadraticBezier(
                    x0 = startX, y0 = startY,
                    xc = controlX, yc = controlY,
                    x1 = endX, y1 = endY,
                    width = lineWidth,
                    segments = 10
                )
            }
        }
    }


    private fun MeshBuilder<UiVertexLayout>.quadraticBezier(
        x0: Float, y0: Float,
        xc: Float, yc: Float,
        x1: Float, y1: Float,
        width: Float,
        segments: Int = 20
    ) {
        var prevX = x0
        var prevY = y0

        for (i in 1..segments) {
            val t = i / segments.toFloat()
            val u = 1 - t

            val x = u.pow(2) * x0 + 2 * u * t * xc + t.pow(2) * x1
            val y = u.pow(2) * y0 + 2 * u * t * yc + t.pow(2) * y1

            line(prevX, prevY, x, y, width)

            prevX = x
            prevY = y
        }
    }
}
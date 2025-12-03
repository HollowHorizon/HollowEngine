package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.PolyUtil
import kotlin.math.pow

class ScratchBlockBackground(
    val color: Color,
    val hasTopNotch: Boolean,
    val hasBottomNotch: Boolean,
) : UiRenderer<UiNode> {

    private val notchWidth = 40f
    private val notchHeight = 10f
    private val notchStartOffset = 30f
    private val cornerRadius = 10f

    override fun renderUi(node: UiNode) {
        with(node) {
            val w = node.widthPx
            val h = node.heightPx
            val x = 0f //node.leftPx
            val y = 0f //node.topPx

            val points = mutableListOf<Vec3f>()

            addBezier(points, x, y + cornerRadius, x, y, x + cornerRadius, y)

            if (hasTopNotch) {
                points.add(Vec3f(x + notchStartOffset, y, 0f))
                points.add(Vec3f(x + notchStartOffset + 5f, y + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth - 5f, y + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth, y, 0f))
            }

            addBezier(points, x + w - cornerRadius, y, x + w, y, x + w, y + cornerRadius)

            addBezier(points, x + w, y + h - cornerRadius, x + w, y + h, x + w - cornerRadius, y + h)

            if (hasBottomNotch) {
                points.add(Vec3f(x + notchStartOffset + notchWidth, y + h, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth - 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset, y + h, 0f))
            }

            addBezier(points, x + cornerRadius, y + h, x, y + h, x, y + h - cornerRadius)


            getPlainBuilder(UiSurface.Companion.LAYER_BACKGROUND).configured(color, clipped = false) {
                fillPolygon(PolyUtil.fillPolygon(points))
            }

            val strokeColor = color.mix(Color.Companion.BLACK, 0.2f)

            getPlainBuilder(UiSurface.Companion.LAYER_BACKGROUND).configured(strokeColor, clipped = false) {
                for (i in 0 until points.size) {
                    val p1 = points[i]
                    val p2 = points[(i + 1) % points.size]
                    line(p1.xy, p2.xy, 2f)
                }
            }
        }
    }

    private fun addBezier(
        points: MutableList<Vec3f>,
        x0: Float,
        y0: Float,
        xc: Float,
        yc: Float,
        x1: Float,
        y1: Float,
    ) {
        val segments = 8
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val u = 1 - t
            val px = u.pow(2) * x0 + 2 * u * t * xc + t.pow(2) * x1
            val py = u.pow(2) * y0 + 2 * u * t * yc + t.pow(2) * y1

            if (points.isEmpty() || i > 0) {
                points.add(Vec3f(px, py, 0f))
            }
        }
    }
}
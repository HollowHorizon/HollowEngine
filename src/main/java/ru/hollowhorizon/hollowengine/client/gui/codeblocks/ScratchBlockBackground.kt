package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.PolyUtil

class ScratchBlockBackground(
    val color: Color,
    val isExpression: Boolean, // true = пазл слева, false = зубчики сверху/снизу
    val hasNext: Boolean // нужно ли рисовать нижний зубчик (для statement)
) : UiRenderer<UiNode> {

    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val r = PuzzleShapes.CORNER_RADIUS

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        if (isExpression) {
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)

            points.add(Vec3f(x + w - r, y, 0f))

            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)

            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

            points.add(Vec3f(x + r, y + h, 0f))

            PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)

            val tyStart = (h - PuzzleShapes.TAB_HEIGHT) / 2f

            points.add(Vec3f(x, tyStart + PuzzleShapes.TAB_HEIGHT, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + PuzzleShapes.TAB_HEIGHT - 5f, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + 5f, 0f))
            points.add(Vec3f(x, tyStart, 0f))

        } else {
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)

            points.add(Vec3f(x + notchX, y, 0f))
            points.add(Vec3f(x + notchX + 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth - 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth, y, 0f))

            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

            if (hasNext) {
                points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
                points.add(Vec3f(x + notchX + notchWidth - 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchX + 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchX, y + h, 0f))
            }

            PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)
        }

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(color, clipped = false) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }

        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(strokeColor, clipped = false) {
            val p = points
            for (i in 0 until p.size) {
                val p1 = p[i]
                val p2 = p[(i + 1) % p.size]
                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}
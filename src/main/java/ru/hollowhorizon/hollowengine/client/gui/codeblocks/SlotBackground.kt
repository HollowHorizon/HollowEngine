package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.PolyUtil

class SlotBackground(val color: Color, val isHovered: Boolean) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f

        val points = mutableListOf<Vec3f>()

        val geom = PuzzleShapes.calculateSafeGeometry(h)
        val r = geom.r
        val tabH = geom.tabH
        val tyStart = geom.tabYStart

        PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y) // Top-Left
        PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r) // Top-Right
        PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h) // Bottom-Right
        PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r) // Bottom-Left

        val tabDepth = PuzzleShapes.TAB_WIDTH

        val bottomTabY = y + tyStart + tabH
        if (bottomTabY < y + h - r - 0.1f) {
            points.add(Vec3f(x, bottomTabY, 0f))
        }

        points.add(Vec3f(x, y + tyStart + tabH, 0f))
        points.add(Vec3f(x + tabDepth, y + tyStart + tabH - 5f * (tabH/20f), 0f))
        points.add(Vec3f(x + tabDepth, y + tyStart + 5f * (tabH/20f), 0f))
        points.add(Vec3f(x, y + tyStart, 0f))

        val topTabY = y + tyStart
        if (topTabY > y + r + 0.1f) {
            points.add(Vec3f(x, topTabY, 0f))
        }

        val bgColor = if(isHovered) color.mix(Color.WHITE, 0.2f) else color

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(bgColor, clipped = true) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        node.getPlainBuilder(UiSurface.LAYER_POPUP).configured(bgColor, clipped = true) {
            PuzzleShapes.drawInnerShadow(
                points,
                width = 4f,
                color = Color.BLACK.withAlpha(0.2f)
            )
        }
    }
}
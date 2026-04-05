package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.PolyUtil

class SlotBackground(
    val color: Color,
    val isHovered: Boolean,
    val zoom: Float,
    val notchInward: Boolean = true,
) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val y = 0f

        val points = mutableListOf<Vec3f>()

        val geom = PuzzleShapes.calculateSafeGeometry(h, zoom)
        val r = geom.r
        val tabH = geom.tabH
        val tyStart = geom.tabYStart

        val tabDepth = Dp(4f * zoom).px

        val startX = if (notchInward) 0f else tabDepth
        val endX = w

        PuzzleShapes.addBezier(points, startX, y + r, startX, y, startX + r, y) // Top-Left
        PuzzleShapes.addBezier(points, endX - r, y, endX, y, endX, y + r) // Top-Right
        PuzzleShapes.addBezier(points, endX, y + h - r, endX, y + h, endX - r, y + h) // Bottom-Right
        PuzzleShapes.addBezier(points, startX + r, y + h, startX, y + h, startX, y + h - r) // Bottom-Left

        val bottomTabY = y + tyStart + tabH
        if (bottomTabY < y + h - r - 0.1f) {
            points.add(Vec3f(startX, bottomTabY, 0f))
        }

        val tabX = if (notchInward) startX + tabDepth else startX - tabDepth

        points.add(Vec3f(startX, y + tyStart + tabH, 0f))
        points.add(Vec3f(tabX, y + tyStart + tabH - 5f * (tabH / 20f), 0f))
        points.add(Vec3f(tabX, y + tyStart + 5f * (tabH / 20f), 0f))
        points.add(Vec3f(startX, y + tyStart, 0f))

        val topTabY = y + tyStart
        if (topTabY > y + r + 0.1f) {
            points.add(Vec3f(startX, topTabY, 0f))
        }

        val bgColor = if(isHovered) color.mix(Color.WHITE, 0.2f) else color

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(bgColor, clipped = true) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        node.getPlainBuilder(UiSurface.LAYER_POPUP).configured(bgColor, clipped = true) {
            PuzzleShapes.drawInnerShadow(points, zoom)
        }
    }
}

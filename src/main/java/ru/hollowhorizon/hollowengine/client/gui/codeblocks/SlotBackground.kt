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
        val r = PuzzleShapes.CORNER_RADIUS

        PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y) // Top-Left corner
        PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r) // Top-Right
        PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h) // Bottom-Right
        PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r) // Bottom-Left

        val yTabStart = y + (h - PuzzleShapes.TAB_HEIGHT - PuzzleShapes.TAB_OFFSET * 2) / 2

        points.add(Vec3f(x, yTabStart + PuzzleShapes.TAB_HEIGHT + PuzzleShapes.TAB_OFFSET, 0f))

        val tabDepth = PuzzleShapes.TAB_WIDTH
        val tyStart = (h - PuzzleShapes.TAB_HEIGHT) / 2f

        points.add(Vec3f(x, tyStart + PuzzleShapes.TAB_HEIGHT, 0f))
        points.add(Vec3f(x + tabDepth, tyStart + PuzzleShapes.TAB_HEIGHT - 5f, 0f))
        points.add(Vec3f(x + tabDepth, tyStart + 5f, 0f))
        points.add(Vec3f(x, tyStart, 0f))

        val bgColor = if(isHovered) color.mix(Color.WHITE, 0.2f) else color

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(bgColor, clipped = false) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
    }
}
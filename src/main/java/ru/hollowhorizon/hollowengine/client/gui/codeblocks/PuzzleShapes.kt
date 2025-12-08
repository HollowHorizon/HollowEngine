package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import kotlin.math.min
import kotlin.math.pow


object PuzzleShapes {
    const val CORNER_RADIUS = 8f
    const val TAB_WIDTH = 8f
    const val TAB_HEIGHT = 20f

    data class SafeGeometry(val r: Float, val tabH: Float, val tabYStart: Float)

    fun calculateSafeGeometry(h: Float): SafeGeometry {
        val r = min(CORNER_RADIUS, h / 2f)

        val availableForTab = h - 2 * r

        var tH = TAB_HEIGHT
        if (tH > availableForTab) {
            tH = availableForTab
            if (tH < 0) tH = 0f
        }

        val tStart = (h - tH) / 2f

        return SafeGeometry(r, tH, tStart)
    }

    fun addBezier(points: MutableList<Vec3f>, x0: Float, y0: Float, xc: Float, yc: Float, x1: Float, y1: Float) {
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
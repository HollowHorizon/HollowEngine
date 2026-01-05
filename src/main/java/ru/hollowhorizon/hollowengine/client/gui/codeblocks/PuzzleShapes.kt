package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiVertexLayout
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.set
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt


object PuzzleShapes {
    val SHADOW_RADIUS = Dp(2f)
    val SHADOW_COLOR = Color.BLACK.withAlpha(0.5f)
    val SHADOW_OFFSET_Y = Dp(1f)

    data class SafeGeometry(val r: Float, val tabH: Float, val tabYStart: Float)

    fun calculateSafeGeometry(h: Float, zoom: Float): SafeGeometry {
        val r = min(Dimensions.PaddingNormal.px * zoom, h / 2f)

        val availableForTab = h - 2 * r
        var tH = Dp(12f).px * zoom

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

    context(builder: MeshBuilder<UiVertexLayout>)
    fun drawShadow(points: List<Vec3f>, zoom: Float) {
        drawStrip(points, SHADOW_RADIUS.px * zoom, SHADOW_COLOR, SHADOW_OFFSET_Y.px * zoom, isInner = false)
    }

    context(builder: MeshBuilder<UiVertexLayout>)
    fun drawInnerShadow(points: List<Vec3f>, zoom: Float) {
        drawStrip(points, SHADOW_RADIUS.px * zoom, SHADOW_COLOR, 0f, isInner = true)
    }

    context(builder: MeshBuilder<UiVertexLayout>)
    private fun drawStrip(
        points: List<Vec3f>,
        width: Float,
        color: Color,
        offsetY: Float,
        isInner: Boolean,
    ) {
        if (points.size < 3) return

        val transparent = color.withAlpha(0f)
        val pSize = points.size

        var firstEdgeIndex = -1
        var firstFadeIndex = -1
        var prevEdgeIndex = -1
        var prevFadeIndex = -1

        for (i in 0 until pSize) {
            val pPrev = points[(i - 1 + pSize) % pSize]
            val pCurr = points[i]
            val pNext = points[(i + 1) % pSize]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val len1 = sqrt(v1x * v1x + v1y * v1y)

            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y
            val len2 = sqrt(v2x * v2x + v2y * v2y)

            val n1x = if (len1 > 0) v1y / len1 else 0f
            val n1y = if (len1 > 0) -v1x / len1 else 1f
            val n2x = if (len2 > 0) v2y / len2 else 0f
            val n2y = if (len2 > 0) -v2x / len2 else 1f

            var nx = n1x + n2x
            var ny = n1y + n2y
            val nLen = sqrt(nx * nx + ny * ny)

            val ex: Float
            val ey: Float

            if (nLen > 0.001f) {
                nx /= nLen
                ny /= nLen
                val dot = n1x * nx + n1y * ny
                val miterScale = (width / max(0.2f, dot))
                ex = nx * miterScale
                ey = ny * miterScale
            } else {
                ex = n1x * width
                ey = n1y * width
            }

            val idxEdge: Int
            val idxFade: Int

            if (isInner) {
                idxEdge = builder.vertex {
                    it.position.set(pCurr.x, pCurr.y, 0f)
                    it.color.set(color)
                }
                idxFade = builder.vertex {
                    it.position.set(pCurr.x - ex, pCurr.y - ey, 0f)
                    it.color.set(transparent)
                }
            } else {
                idxEdge = builder.vertex {
                    it.position.set(pCurr.x, pCurr.y, 0f)
                    it.color.set(color)
                }
                idxFade = builder.vertex {
                    it.position.set(pCurr.x + ex, pCurr.y + ey + offsetY, 0f)
                    it.color.set(transparent)
                }
            }

            if (i > 0) {
                builder.addTriIndices(prevEdgeIndex, prevFadeIndex, idxFade)
                builder.addTriIndices(prevEdgeIndex, idxFade, idxEdge)
            } else {
                firstEdgeIndex = idxEdge
                firstFadeIndex = idxFade
            }

            prevEdgeIndex = idxEdge
            prevFadeIndex = idxFade
        }

        if (prevEdgeIndex != -1 && firstEdgeIndex != -1) {
            builder.addTriIndices(prevEdgeIndex, prevFadeIndex, firstFadeIndex)
            builder.addTriIndices(prevEdgeIndex, firstFadeIndex, firstEdgeIndex)
        }
    }

    context(builder: MeshBuilder<UiVertexLayout>)
    fun drawStroke(points: List<Vec3f>, width: Float, color: Color, ignoreLast: Boolean) {
        val pSize = points.size
        for (i in 0 until pSize) {
            val p1 = points[i]
            val p2 = points[(i + 1) % pSize]
            if (i == pSize - 1 && ignoreLast) return
            builder.withColor(color) {
                line(p1.x, p1.y, p2.x, p2.y, width)
            }
        }
    }
}
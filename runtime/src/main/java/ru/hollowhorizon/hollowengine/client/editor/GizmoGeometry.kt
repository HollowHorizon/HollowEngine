package ru.hollowhorizon.hollowengine.client.editor

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.utils.math.rotateBy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A 2D point in the overlay's logical coordinate space. */
data class Pt(val x: Float, val y: Float)

/** Which manipulator a screen handle drives. */
enum class GizmoHandleId {
    AXIS_X, AXIS_Y, AXIS_Z,
    PLANE_X, PLANE_Y, PLANE_Z,
    ROTATE_X, ROTATE_Y, ROTATE_Z,
    SCALE_X, SCALE_Y, SCALE_Z,
    CENTER, SCALE_UNIFORM,
}

/** The screen shape a handle is hit-tested against (see [GizmoPicker]). */
sealed interface PickPrimitive {
    data class Line(val points: List<Pt>, val closed: Boolean) : PickPrimitive
    data class Polygon(val points: List<Pt>) : PickPrimitive
    data class Disc(val center: Pt, val radius: Float) : PickPrimitive
}

/**
 * One interactive gizmo handle resolved into screen space for a frame. [renderLines] are the polylines
 * to stroke, each paired with whether it should be closed; [fillPolygon] is an optional filled area
 * (plane squares, scale cubes). [worldOrigin]/[worldAxis] feed the manipulator.
 */
data class GizmoHandle(
    val id: GizmoHandleId,
    val worldOrigin: Vec3,
    val worldAxis: Vec3?,
    val renderLines: List<GizmoStroke>,
    val fillPolygon: List<Pt>?,
    val color: UiColor,
    val width: Float,
    val depth: Float,
    val pick: PickPrimitive,
)

data class GizmoStroke(val points: List<Pt>, val closed: Boolean = false)

object GizmoColors {
    val AXIS_X = UiColor(0.96f, 0.30f, 0.28f)
    val AXIS_Y = UiColor(0.52f, 0.80f, 0.32f)
    val AXIS_Z = UiColor(0.28f, 0.58f, 0.98f)
    val CENTER = UiColor(0.95f, 0.96f, 0.98f)

    val BOUNDS = UiColor(0.68f, 0.70f, 0.74f, 0.55f)
    val BOUNDS_HOVER = UiColor(0.40f, 0.90f, 1f, 0.85f)
    val BOUNDS_ACTIVE = UiColor(1f, 0.80f, 0.25f, 0.95f)

    val LIGHT = UiColor(0.82f, 0.84f, 0.88f, 0.92f)
    val LIGHT_HOVER = UiColor(0.40f, 0.90f, 1f, 0.95f)
    val LIGHT_ACTIVE = BOUNDS_ACTIVE

    fun highlighted(base: UiColor): UiColor = UiColor(
        (base.red + 0.28f).coerceAtMost(1f),
        (base.green + 0.28f).coerceAtMost(1f),
        (base.blue + 0.28f).coerceAtMost(1f),
        base.alpha,
    )
}

/**
 * Builds the screen-space geometry for the gizmo.
 */
object GizmoGeometry {
    private const val AXIS_LENGTH_PX = 38f
    private const val ARROW_HEAD_PX = 8f
    private const val PLANE_OFFSET = 0.36f
    private const val PLANE_HALF = 0.17f
    private const val RING_RADIUS_PX = 36f
    private const val RING_SEGMENTS = 72
    private const val CENTER_RADIUS_PX = 7f
    private const val SCALE_CUBE_PX = 6.5f
    private const val CIRCLE_SEGMENTS = 48

    private const val AXIS_WIDTH = 1.7f
    private const val RING_WIDTH = 1.7f

    fun buildHandles(translation: Vec3f, rotation: QuatF, mode: GizmoEditMode, cullRings: Boolean = true): List<GizmoHandle> {
        val projector = WorldToScreenProjector

        val origin = Vec3(translation.x.toDouble(), translation.y.toDouble(), translation.z.toDouble())
        val basis = if (mode == GizmoEditMode.TRANSLATE) QuatF.IDENTITY else rotation
        val ax = Vec3f.X_AXIS.rotateBy(basis)
        val ay = Vec3f.Y_AXIS.rotateBy(basis)
        val az = Vec3f.Z_AXIS.rotateBy(basis)
        val perPixel = projector.worldPerPixel(origin)
        val originScreen = projector.project(origin) ?: return emptyList()
        val originPt = Pt(originScreen.x, originScreen.y)

        return when (mode) {
            GizmoEditMode.TRANSLATE -> buildList {
                axisArrow(GizmoHandleId.AXIS_X, origin, ax, perPixel, GizmoColors.AXIS_X)?.let(::add)
                axisArrow(GizmoHandleId.AXIS_Y, origin, ay, perPixel, GizmoColors.AXIS_Y)?.let(::add)
                axisArrow(GizmoHandleId.AXIS_Z, origin, az, perPixel, GizmoColors.AXIS_Z)?.let(::add)
                planeQuad(GizmoHandleId.PLANE_X, origin, ax, ay, az, perPixel, GizmoColors.AXIS_X)?.let(::add)
                planeQuad(GizmoHandleId.PLANE_Y, origin, ay, az, ax, perPixel, GizmoColors.AXIS_Y)?.let(::add)
                planeQuad(GizmoHandleId.PLANE_Z, origin, az, ax, ay, perPixel, GizmoColors.AXIS_Z)?.let(::add)
                add(discHandle(GizmoHandleId.CENTER, origin, originPt, CENTER_RADIUS_PX, GizmoColors.CENTER, originScreen.depth))
            }

            GizmoEditMode.ROTATE -> buildList {
                rotationRing(GizmoHandleId.ROTATE_X, origin, ax, ay, az, perPixel, cullRings, GizmoColors.AXIS_X)?.let(::add)
                rotationRing(GizmoHandleId.ROTATE_Y, origin, ay, az, ax, perPixel, cullRings, GizmoColors.AXIS_Y)?.let(::add)
                rotationRing(GizmoHandleId.ROTATE_Z, origin, az, ax, ay, perPixel, cullRings, GizmoColors.AXIS_Z)?.let(::add)
            }

            GizmoEditMode.SCALE -> buildList {
                scaleAxis(GizmoHandleId.SCALE_X, origin, ax, perPixel, GizmoColors.AXIS_X)?.let(::add)
                scaleAxis(GizmoHandleId.SCALE_Y, origin, ay, perPixel, GizmoColors.AXIS_Y)?.let(::add)
                scaleAxis(GizmoHandleId.SCALE_Z, origin, az, perPixel, GizmoColors.AXIS_Z)?.let(::add)
                add(cubeHandle(GizmoHandleId.SCALE_UNIFORM, origin, originPt, SCALE_CUBE_PX + 1.5f, GizmoColors.CENTER, originScreen.depth))
            }
        }
    }

    private fun axisArrow(id: GizmoHandleId, origin: Vec3, axis: Vec3f, perPixel: Float, color: UiColor): GizmoHandle? {
        val projector = WorldToScreenProjector
        val length = perPixel * AXIS_LENGTH_PX
        val tipWorld = origin.add(axis.x * length.toDouble(), axis.y * length.toDouble(), axis.z * length.toDouble())
        val start = projector.project(origin) ?: return null
        val end = projector.project(tipWorld) ?: return null
        if (!start.onScreen || !end.onScreen) return null
        val startPt = Pt(start.x, start.y)
        val endPt = Pt(end.x, end.y)
        val lines = ArrayList<GizmoStroke>()
        lines += GizmoStroke(listOf(startPt, endPt))
        lines += arrowHead(startPt, endPt)
        return GizmoHandle(
            id, origin, worldVec(axis), lines, fillPolygon = null, color = color,
            width = AXIS_WIDTH, depth = end.depth, pick = PickPrimitive.Line(listOf(startPt, endPt), closed = false),
        )
    }

    private fun arrowHead(start: Pt, end: Pt): GizmoStroke {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1e-3f) return GizmoStroke(emptyList())
        val ux = dx / len
        val uy = dy / len
        val px = -uy
        val py = ux
        val baseX = end.x - ux * ARROW_HEAD_PX
        val baseY = end.y - uy * ARROW_HEAD_PX
        val half = ARROW_HEAD_PX * 0.5f
        val left = Pt(baseX + px * half, baseY + py * half)
        val right = Pt(baseX - px * half, baseY - py * half)
        return GizmoStroke(listOf(left, end, right), closed = false)
    }

    private fun planeQuad(id: GizmoHandleId, origin: Vec3, normal: Vec3f, spanA: Vec3f, spanB: Vec3f, perPixel: Float, color: UiColor): GizmoHandle? {
        val projector = WorldToScreenProjector
        val length = perPixel * AXIS_LENGTH_PX
        val offset = length * PLANE_OFFSET
        val half = length * PLANE_HALF
        val center = origin.add(
            (spanA.x + spanB.x).toDouble() * offset,
            (spanA.y + spanB.y).toDouble() * offset,
            (spanA.z + spanB.z).toDouble() * offset,
        )
        val corners = listOf(
            cornerWorld(center, spanA, spanB, -half, -half),
            cornerWorld(center, spanA, spanB, half, -half),
            cornerWorld(center, spanA, spanB, half, half),
            cornerWorld(center, spanA, spanB, -half, half),
        )
        val screen = corners.map { projector.project(it) ?: return null }
        if (screen.any { !it.onScreen }) return null
        val pts = screen.map { Pt(it.x, it.y) }
        return GizmoHandle(
            id, origin, worldVec(normal), listOf(GizmoStroke(pts, closed = true)), fillPolygon = pts,
            color = color, width = AXIS_WIDTH, depth = screen.map { it.depth }.average().toFloat(),
            pick = PickPrimitive.Polygon(pts),
        )
    }

    private fun cornerWorld(center: Vec3, a: Vec3f, b: Vec3f, ka: Float, kb: Float): Vec3 = center.add(
        (a.x * ka + b.x * kb).toDouble(),
        (a.y * ka + b.y * kb).toDouble(),
        (a.z * ka + b.z * kb).toDouble(),
    )

    private fun rotationRing(id: GizmoHandleId, origin: Vec3, axis: Vec3f, u: Vec3f, v: Vec3f, perPixel: Float, cull: Boolean, color: UiColor): GizmoHandle? {
        val projector = WorldToScreenProjector
        val radius = perPixel * RING_RADIUS_PX
        val worldPts = ArrayList<Vec3>(RING_SEGMENTS + 1)
        val pts = ArrayList<Pt>(RING_SEGMENTS + 1)
        var depthSum = 0f
        for (i in 0..RING_SEGMENTS) {
            val angle = i.toDouble() / RING_SEGMENTS * Math.PI * 2.0
            val c = cos(angle).toFloat() * radius
            val s = sin(angle).toFloat() * radius
            val world = origin.add(
                (u.x * c + v.x * s).toDouble(),
                (u.y * c + v.y * s).toDouble(),
                (u.z * c + v.z * s).toDouble(),
            )
            val projected = projector.project(world) ?: return null
            if (!projected.onScreen) return null
            worldPts += world
            pts += Pt(projected.x, projected.y)
            depthSum += projected.depth
        }
        val depthMean = depthSum / pts.size
        val pick = PickPrimitive.Line(pts, closed = true)

        val camDir = projector.cameraPosition.subtract(origin)
        val camLen = camDir.length()

        val faceOn = camLen < 1e-6 || abs(worldVec(axis).dot(camDir)) / camLen > 0.9
        if (!cull || faceOn) {
            return GizmoHandle(
                id, origin, worldVec(axis), listOf(GizmoStroke(pts, closed = true)), fillPolygon = null,
                color = color, width = RING_WIDTH, depth = depthMean, pick = pick,
            )
        }

        val front = BooleanArray(RING_SEGMENTS + 1) { worldPts[it].subtract(origin).dot(camDir) > 0.0 }
        val arcs = ArrayList<GizmoStroke>()
        var current: ArrayList<Pt>? = null
        for (i in 0 until RING_SEGMENTS) {
            if (front[i] && front[i + 1]) {
                val run = current ?: ArrayList<Pt>().also { current = it; it += pts[i] }
                run += pts[i + 1]
            } else {
                current = null
            }
        }
        current?.let { arcs += GizmoStroke(it) }
        return GizmoHandle(
            id, origin, worldVec(axis), arcs, fillPolygon = null, color = color,
            width = RING_WIDTH, depth = depthMean, pick = pick,
        )
    }

    private fun scaleAxis(id: GizmoHandleId, origin: Vec3, axis: Vec3f, perPixel: Float, color: UiColor): GizmoHandle? {
        val projector = WorldToScreenProjector
        val length = perPixel * AXIS_LENGTH_PX
        val tipWorld = origin.add(axis.x * length.toDouble(), axis.y * length.toDouble(), axis.z * length.toDouble())
        val start = projector.project(origin) ?: return null
        val end = projector.project(tipWorld) ?: return null
        if (!start.onScreen || !end.onScreen) return null
        val startPt = Pt(start.x, start.y)
        val endPt = Pt(end.x, end.y)
        val cube = squareAround(endPt, SCALE_CUBE_PX)
        return GizmoHandle(
            id, origin, worldVec(axis),
            listOf(GizmoStroke(listOf(startPt, endPt)), GizmoStroke(cube, closed = true)),
            fillPolygon = cube, color = color, width = AXIS_WIDTH, depth = end.depth,
            pick = PickPrimitive.Line(listOf(startPt, endPt), closed = false),
        )
    }

    private fun cubeHandle(id: GizmoHandleId, origin: Vec3, center: Pt, half: Float, color: UiColor, depth: Float): GizmoHandle {
        val cube = squareAround(center, half)
        return GizmoHandle(
            id, origin, worldAxis = null, listOf(GizmoStroke(cube, closed = true)), fillPolygon = cube,
            color = color, width = RING_WIDTH, depth = depth, pick = PickPrimitive.Disc(center, half + 1.5f),
        )
    }

    private fun discHandle(id: GizmoHandleId, origin: Vec3, center: Pt, radiusPx: Float, color: UiColor, depth: Float): GizmoHandle {
        return GizmoHandle(
            id, origin, worldAxis = null, listOf(GizmoStroke(screenCircle(center, radiusPx), closed = true)),
            fillPolygon = null, color = color, width = 2f, depth = depth, pick = PickPrimitive.Disc(center, radiusPx),
        )
    }

    private fun squareAround(center: Pt, half: Float): List<Pt> = listOf(
        Pt(center.x - half, center.y - half),
        Pt(center.x + half, center.y - half),
        Pt(center.x + half, center.y + half),
        Pt(center.x - half, center.y + half),
    )

    private fun screenCircle(center: Pt, radius: Float): List<Pt> {
        val pts = ArrayList<Pt>(CIRCLE_SEGMENTS + 1)
        for (i in 0..CIRCLE_SEGMENTS) {
            val angle = i.toDouble() / CIRCLE_SEGMENTS * Math.PI * 2.0
            pts += Pt(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
        }
        return pts
    }

    /**
     * The filled sector between [startAngle] and [endAngle] (radians) in the plane perpendicular to
     * [axis], for the rotation drag readout. Basis matches [GizmoManipulator.anglePlane].
     */
    fun buildRotationSector(origin: Vec3, axis: Vec3, startAngle: Double, endAngle: Double, perPixel: Float): List<Pt>? {
        val projector = WorldToScreenProjector
        val radius = perPixel * RING_RADIUS_PX * 0.92f
        val u = perpendicular(axis)
        val v = axis.cross(u)
        val originScreen = projector.project(origin) ?: return null
        if (!originScreen.onScreen) return null
        val steps = 48
        val pts = ArrayList<Pt>(steps + 2)
        pts += Pt(originScreen.x, originScreen.y)
        for (i in 0..steps) {
            val angle = startAngle + (endAngle - startAngle) * (i.toDouble() / steps)
            val c = cos(angle) * radius
            val s = sin(angle) * radius
            val world = origin.add(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s)
            val projected = projector.project(world) ?: return null
            if (!projected.onScreen) return null
            pts += Pt(projected.x, projected.y)
        }
        return pts
    }

    private fun perpendicular(n: Vec3): Vec3 {
        val reference = if (abs(n.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        return n.cross(reference).normalize()
    }

    private fun worldVec(v: Vec3f): Vec3 = Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())

    /** The 12 edges of [bounds] projected into screen space, or empty when any corner is off-screen. */
    fun buildBoundsEdges(bounds: AABB): List<List<Pt>> {
        val projector = WorldToScreenProjector
        val corners = arrayOf(
            Vec3(bounds.minX, bounds.minY, bounds.minZ), Vec3(bounds.maxX, bounds.minY, bounds.minZ),
            Vec3(bounds.maxX, bounds.minY, bounds.maxZ), Vec3(bounds.minX, bounds.minY, bounds.maxZ),
            Vec3(bounds.minX, bounds.maxY, bounds.minZ), Vec3(bounds.maxX, bounds.maxY, bounds.minZ),
            Vec3(bounds.maxX, bounds.maxY, bounds.maxZ), Vec3(bounds.minX, bounds.maxY, bounds.maxZ),
        )
        val screen = arrayOfNulls<Pt>(8)
        for (i in corners.indices) {
            val p = projector.project(corners[i]) ?: return emptyList()
            if (!p.onScreen) return emptyList()
            screen[i] = Pt(p.x, p.y)
        }
        val edges = intArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7,
        )
        val result = ArrayList<List<Pt>>(12)
        var i = 0
        while (i < edges.size) {
            result += listOf(screen[edges[i]]!!, screen[edges[i + 1]]!!)
            i += 2
        }
        return result
    }

    /** Projects a world-space polyline; returns null if any vertex is off-screen. */
    fun projectPolyline(points: List<Vec3>): List<Pt>? {
        val projector = WorldToScreenProjector
        val result = ArrayList<Pt>(points.size)
        for (point in points) {
            val p = projector.project(point) ?: return null
            if (!p.onScreen) return null
            result += Pt(p.x, p.y)
        }
        return result
    }
}

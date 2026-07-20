package ru.hollowhorizon.hollowengine.client.editor

import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.utils.math.deg
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.sqrt

/** A world transform value passed between the manipulator and the editor. */
data class GizmoTransformValues(
    val translation: Vec3f,
    val rotation: QuatF,
    val scale: Vec3f,
)

/**
 * A single in-progress drag. Created by [GizmoManipulator.begin] on grab and advanced by
 * [GizmoManipulator.update] on pointer motion. Holds the reference captured at grab time so motion is
 * measured relative to the initial contact point.
 */
class GizmoDrag internal constructor(
    val handleId: GizmoHandleId,
    internal val origin: Vec3,
    internal val axis: Vec3?,
    internal val start: GizmoTransformValues,
) {
    internal var referenceParam = 0.0
    internal var referenceHit: Vec3? = null
    internal var planeNormal: Vec3? = null
    internal var referenceAngle = 0.0
    internal var currentAngle = 0.0
    internal var referenceDistance = 0f
    internal var valid = false

    /** Rotation drag start/current angle (radians) in the handle plane, for the angle-sector readout. */
    val startAngle: Double get() = referenceAngle
    val angle: Double get() = currentAngle

    /** Magnitude of the current change, for the on-screen value label (meters / degrees / factor). */
    var labelValue: Double = 0.0
        internal set
}

/**
 * Screen-to-world manipulation math for the gizmo.
 */
object GizmoManipulator {
    fun begin(handle: GizmoHandle, values: GizmoTransformValues, pointerX: Float, pointerY: Float): GizmoDrag {
        val axis = handle.worldAxis?.normalize()
        val drag = GizmoDrag(handle.id, handle.worldOrigin, axis, values)
        val ray = WorldToScreenProjector.screenRay(pointerX, pointerY)
        when (handle.id) {
            GizmoHandleId.AXIS_X, GizmoHandleId.AXIS_Y, GizmoHandleId.AXIS_Z,
            GizmoHandleId.SCALE_X, GizmoHandleId.SCALE_Y, GizmoHandleId.SCALE_Z -> {
                if (ray != null && axis != null) {
                    val t = closestParamOnAxis(ray, handle.worldOrigin, axis)
                    if (t != null) {
                        drag.referenceParam = t
                        drag.valid = true
                    }
                }
            }

            GizmoHandleId.PLANE_X, GizmoHandleId.PLANE_Y, GizmoHandleId.PLANE_Z -> {
                if (ray != null && axis != null) {
                    val hit = rayPlane(ray, handle.worldOrigin, axis)
                    if (hit != null) {
                        drag.referenceHit = hit
                        drag.valid = true
                    }
                }
            }

            GizmoHandleId.ROTATE_X, GizmoHandleId.ROTATE_Y, GizmoHandleId.ROTATE_Z -> {
                if (ray != null && axis != null) {
                    val angle = anglePlane(ray, handle.worldOrigin, axis)
                    if (angle != null) {
                        drag.referenceAngle = angle
                        drag.currentAngle = angle
                        drag.valid = true
                    }
                }
            }

            GizmoHandleId.CENTER -> {
                val toCamera = WorldToScreenProjector.cameraPosition.subtract(handle.worldOrigin)
                val length = toCamera.length()
                if (ray != null && length > 1e-6) {
                    val normal = toCamera.scale(1.0 / length)
                    drag.planeNormal = normal
                    val hit = rayPlane(ray, handle.worldOrigin, normal)
                    if (hit != null) {
                        drag.referenceHit = hit
                        drag.valid = true
                    }
                }
            }

            GizmoHandleId.SCALE_UNIFORM -> {
                val originScreen = WorldToScreenProjector.project(handle.worldOrigin)
                if (originScreen != null) {
                    drag.referenceDistance = distance(originScreen.x, originScreen.y, pointerX, pointerY)
                    drag.valid = drag.referenceDistance > 1e-3f
                }
            }
        }
        return drag
    }

    /** Advances [drag] to the pointer and returns the new transform, or null if nothing changed. */
    fun update(
        drag: GizmoDrag,
        pointerX: Float,
        pointerY: Float,
        modifiers: Int,
    ): GizmoTransformValues? {
        if (!drag.valid) return null
        val fine = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val snap = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        val speed = if (fine) 0.1 else 1.0

        return when (drag.handleId) {
            GizmoHandleId.AXIS_X, GizmoHandleId.AXIS_Y, GizmoHandleId.AXIS_Z ->
                updateAxis(drag, pointerX, pointerY, speed, snap, fine)

            GizmoHandleId.PLANE_X, GizmoHandleId.PLANE_Y, GizmoHandleId.PLANE_Z ->
                updatePlane(drag, pointerX, pointerY, speed, snap, fine)

            GizmoHandleId.ROTATE_X, GizmoHandleId.ROTATE_Y, GizmoHandleId.ROTATE_Z ->
                updateRotate(drag, pointerX, pointerY, speed, snap, fine)

            GizmoHandleId.SCALE_X, GizmoHandleId.SCALE_Y, GizmoHandleId.SCALE_Z ->
                updateScaleAxis(drag, pointerX, pointerY, speed, snap, fine)

            GizmoHandleId.CENTER ->
                updateViewPlane(drag, pointerX, pointerY, speed, snap, fine)

            GizmoHandleId.SCALE_UNIFORM ->
                updateScale(drag, pointerX, pointerY, speed, snap, fine)
        }
    }

    private fun updateViewPlane(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val normal = drag.planeNormal ?: return null
        val reference = drag.referenceHit ?: return null
        val ray = WorldToScreenProjector.screenRay(x, y) ?: return null
        val hit = rayPlane(ray, drag.origin, normal) ?: return null
        var dx = (hit.x - reference.x) * speed
        var dy = (hit.y - reference.y) * speed
        var dz = (hit.z - reference.z) * speed
        if (snap) {
            val tick = translationTick(fine)
            dx = round(dx / tick) * tick
            dy = round(dy / tick) * tick
            dz = round(dz / tick) * tick
        }
        drag.labelValue = sqrt(dx * dx + dy * dy + dz * dz)
        return drag.start.copy(translation = drag.start.translation + Vec3f(dx.toFloat(), dy.toFloat(), dz.toFloat()))
    }

    private fun updateAxis(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val axis = drag.axis ?: return null
        val ray = WorldToScreenProjector.screenRay(x, y) ?: return null
        val t = closestParamOnAxis(ray, drag.origin, axis) ?: return null
        var delta = (t - drag.referenceParam) * speed
        if (snap) delta = round(delta / translationTick(fine)) * translationTick(fine)
        drag.labelValue = delta
        val offset = Vec3f((axis.x * delta).toFloat(), (axis.y * delta).toFloat(), (axis.z * delta).toFloat())
        return drag.start.copy(translation = drag.start.translation + offset)
    }

    private fun updatePlane(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val axis = drag.axis ?: return null
        val reference = drag.referenceHit ?: return null
        val ray = WorldToScreenProjector.screenRay(x, y) ?: return null
        val hit = rayPlane(ray, drag.origin, axis) ?: return null
        var dx = (hit.x - reference.x) * speed
        var dy = (hit.y - reference.y) * speed
        var dz = (hit.z - reference.z) * speed
        if (snap) {
            val tick = translationTick(fine)
            dx = round(dx / tick) * tick
            dy = round(dy / tick) * tick
            dz = round(dz / tick) * tick
        }
        drag.labelValue = sqrt(dx * dx + dy * dy + dz * dz)
        val offset = Vec3f(dx.toFloat(), dy.toFloat(), dz.toFloat())
        return drag.start.copy(translation = drag.start.translation + offset)
    }

    private fun updateRotate(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val axis = drag.axis ?: return null
        val ray = WorldToScreenProjector.screenRay(x, y) ?: return null
        val angle = anglePlane(ray, drag.origin, axis) ?: return null
        drag.currentAngle = drag.referenceAngle + shortestAngle(angle - drag.referenceAngle)
        var deltaDeg = Math.toDegrees(shortestAngle(angle - drag.referenceAngle)) * speed
        if (snap) deltaDeg = round(deltaDeg / rotationTick(fine)) * rotationTick(fine)
        drag.labelValue = deltaDeg
        val axisF = Vec3f(axis.x.toFloat(), axis.y.toFloat(), axis.z.toFloat())
        val delta = QuatF(deltaDeg.toFloat().deg, axisF)
        val rotation = (delta * drag.start.rotation).normed()
        return drag.start.copy(rotation = rotation)
    }

    private fun updateScale(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val originScreen = WorldToScreenProjector.project(drag.origin) ?: return null
        val current = distance(originScreen.x, originScreen.y, x, y)
        if (drag.referenceDistance <= 1e-3f) return null
        var factor = (current / drag.referenceDistance).toDouble()
        factor = 1.0 + (factor - 1.0) * speed
        if (snap) factor = (round(factor / scaleTick(fine)) * scaleTick(fine)).coerceAtLeast(scaleTick(fine))
        factor = factor.coerceIn(0.01, 100.0)
        drag.labelValue = factor
        val scale = Vec3f(
            (drag.start.scale.x * factor).toFloat(),
            (drag.start.scale.y * factor).toFloat(),
            (drag.start.scale.z * factor).toFloat(),
        )
        return drag.start.copy(scale = scale)
    }

    private fun updateScaleAxis(drag: GizmoDrag, x: Float, y: Float, speed: Double, snap: Boolean, fine: Boolean): GizmoTransformValues? {
        val axis = drag.axis ?: return null
        if (abs(drag.referenceParam) < 1e-4) return null
        val ray = WorldToScreenProjector.screenRay(x, y) ?: return null
        val t = closestParamOnAxis(ray, drag.origin, axis) ?: return null
        var factor = t / drag.referenceParam
        factor = 1.0 + (factor - 1.0) * speed
        if (snap) factor = round(factor / scaleTick(fine)) * scaleTick(fine)
        factor = factor.coerceIn(0.01, 100.0)
        drag.labelValue = factor
        val s = drag.start.scale
        val scale = when (drag.handleId) {
            GizmoHandleId.SCALE_X -> Vec3f((s.x * factor).toFloat(), s.y, s.z)
            GizmoHandleId.SCALE_Y -> Vec3f(s.x, (s.y * factor).toFloat(), s.z)
            GizmoHandleId.SCALE_Z -> Vec3f(s.x, s.y, (s.z * factor).toFloat())
            else -> return null
        }
        return drag.start.copy(scale = scale)
    }

    private fun closestParamOnAxis(ray: WorldRay, origin: Vec3, axis: Vec3): Double? {
        val d1 = ray.direction
        val r = ray.origin.subtract(origin)
        val b = d1.dot(axis)
        val denom = 1.0 - b * b
        if (abs(denom) < 1e-6) return null
        val d = d1.dot(r)
        val e = axis.dot(r)
        return (e - b * d) / denom
    }

    private fun rayPlane(ray: WorldRay, origin: Vec3, normal: Vec3): Vec3? {
        val denom = ray.direction.dot(normal)
        if (abs(denom) < 1e-6) return null
        val t = origin.subtract(ray.origin).dot(normal) / denom
        if (t < 0) return null
        return ray.origin.add(ray.direction.scale(t))
    }

    private fun anglePlane(ray: WorldRay, origin: Vec3, normal: Vec3): Double? {
        val hit = rayPlane(ray, origin, normal) ?: return null
        val radial = hit.subtract(origin)
        val u = perpendicular(normal)
        val v = normal.cross(u)
        val cu = radial.dot(u)
        val cv = radial.dot(v)
        if (abs(cu) < 1e-9 && abs(cv) < 1e-9) return null
        return atan2(cv, cu)
    }

    private fun perpendicular(n: Vec3): Vec3 {
        val reference = if (abs(n.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        return n.cross(reference).normalize()
    }

    private fun shortestAngle(angle: Double): Double {
        var a = angle
        while (a > Math.PI) a -= Math.PI * 2
        while (a < -Math.PI) a += Math.PI * 2
        return a
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun translationTick(fine: Boolean) = if (fine) 0.1 else 1.0
    private fun rotationTick(fine: Boolean) = if (fine) 1.0 else 5.0
    private fun scaleTick(fine: Boolean) = if (fine) 0.01 else 0.1
}

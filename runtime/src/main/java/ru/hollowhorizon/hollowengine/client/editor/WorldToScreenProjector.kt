package ru.hollowhorizon.hollowengine.client.editor

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f
import ru.hollowhorizon.hollowengine.client.gui.scripting.HollowIdeScale
import kotlin.math.tan

/**
 * A world point projected into the HollowUi overlay's logical coordinate space.
 */
data class ScreenPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val onScreen: Boolean,
)

/** A world-space ray reconstructed by un-projecting a screen point. */
data class WorldRay(
    val origin: Vec3,
    val direction: Vec3,
)

object WorldToScreenProjector {
    private val combined = Matrix4f()
    private val inverse = Matrix4f()
    private var camX = 0.0
    private var camY = 0.0
    private var camZ = 0.0
    private var fovYRadians = 1.22f
    private var logicalWidth = 1f
    private var logicalHeight = 1f

    private const val NEAR_PLANE_EPSILON = 0.05f

    private val scratch = Vector4f()

    fun capture(view: Matrix4f, projection: Matrix4f, cameraPosition: Vec3, fovDegrees: Float) {
        combined.set(projection).mul(view)
        combined.invert(inverse)
        camX = cameraPosition.x
        camY = cameraPosition.y
        camZ = cameraPosition.z
        fovYRadians = Math.toRadians(fovDegrees.toDouble()).toFloat()
        logicalWidth = HollowIdeScale.scaledWidth()
        logicalHeight = HollowIdeScale.scaledHeight()
    }

    val width: Float get() = logicalWidth
    val height: Float get() = logicalHeight
    val cameraPosition: Vec3 get() = Vec3(camX, camY, camZ)

    fun screenCenter(): Pair<Float, Float> = logicalWidth * 0.5f to logicalHeight * 0.5f

    fun project(world: Vec3): ScreenPoint? = project(world.x, world.y, world.z)

    fun project(worldX: Double, worldY: Double, worldZ: Double): ScreenPoint {
        scratch.set((worldX - camX).toFloat(), (worldY - camY).toFloat(), (worldZ - camZ).toFloat(), 1f)
        combined.transform(scratch)
        val w = scratch.w
        if (!w.isFinite() || w <= NEAR_PLANE_EPSILON) return ScreenPoint(0f, 0f, w, onScreen = false)
        val ndcX = scratch.x / w
        val ndcY = scratch.y / w
        val screenX = (ndcX * 0.5f + 0.5f) * logicalWidth
        val screenY = (0.5f - ndcY * 0.5f) * logicalHeight
        if (!screenX.isFinite() || !screenY.isFinite()) return ScreenPoint(0f, 0f, w, onScreen = false)
        return ScreenPoint(screenX, screenY, depth = w, onScreen = true)
    }

    /** Un-projects a logical screen point into a world-space ray (origin on the near plane). */
    fun screenRay(screenX: Float, screenY: Float): WorldRay? {
        val ndcX = screenX / logicalWidth * 2f - 1f
        val ndcY = 1f - screenY / logicalHeight * 2f
        val near = unproject(ndcX, ndcY, -1f) ?: return null
        val far = unproject(ndcX, ndcY, 1f) ?: return null
        val dir = far.subtract(near)
        val length = dir.length()
        if (length < 1.0e-9) return null
        return WorldRay(near, dir.scale(1.0 / length))
    }

    private fun unproject(ndcX: Float, ndcY: Float, ndcZ: Float): Vec3? {
        scratch.set(ndcX, ndcY, ndcZ, 1f)
        inverse.transform(scratch)
        val w = scratch.w
        if (w == 0f) return null
        return Vec3(
            scratch.x / w + camX,
            scratch.y / w + camY,
            scratch.z / w + camZ,
        )
    }

    fun worldPerPixel(world: Vec3): Float {
        if (logicalHeight <= 0f) return 0.05f
        val distance = world.distanceTo(cameraPosition).toFloat()
        val worldHeightAtDepth = 2f * tan(fovYRadians * 0.5f) * distance
        return (worldHeightAtDepth / logicalHeight).coerceAtLeast(1.0e-5f)
    }
}

package ru.hollowhorizon.hollowengine.client.render.lighting

import de.fabmax.kool.math.Vec3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import ru.hollowhorizon.hollowengine.client.utils.math.rotateBy
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object ClusteredLightingConfig {
    const val FEATURE_FLAG = "HE_CLUSTERED_LIGHTING"

    const val TILE_SIZE = 16
    const val Z_SLICES = 12
    const val MAX_LIGHTS_PER_CLUSTER = 32
    const val MAX_VOLUMETRIC_LIGHTS_PER_TILE = 64

    const val CORE_LIGHT_BINDING = 28
    const val POINT_LIGHT_BINDING = 29
    const val SPOT_LIGHT_BINDING = 30
    const val SHADOW_SETTINGS_BINDING = 31
    const val VOLUMETRIC_FOG_BINDING = 32
    const val FLARE_BINDING = 33
    const val CLUSTER_INDEX_BINDING = 34
    const val VOLUMETRIC_TILE_INDEX_BINDING = 35
    const val SHADOW_DATA_BINDING = 36

    const val CORE_LIGHT_STRIDE = 48
    const val POINT_LIGHT_STRIDE = 16
    const val SPOT_LIGHT_STRIDE = 32
    const val SHADOW_SETTINGS_STRIDE = 16
    const val VOLUMETRIC_FOG_STRIDE = 16
    const val FLARE_STRIDE = 32

    const val MAX_SPOT_SHADOW_LIGHTS = 16
    const val MAX_POINT_SHADOW_LIGHTS = 4
    const val STATIC_SHADOW_UPDATES_PER_FRAME = 2
    const val STATIC_SHADOW_REFRESH_INTERVAL_FRAMES = 12

    const val SPOT_SHADOW_ATLAS_SIZE = 2048
    const val SPOT_SHADOW_TILE_SIZE = 512
    const val POINT_SHADOW_ATLAS_WIDTH = 2048
    const val POINT_SHADOW_ATLAS_HEIGHT = 1024
    const val POINT_SHADOW_FACE_SIZE = 256
}

data class ClusterClipPlanes(
    val nearPlane: Float,
    val farPlane: Float,
)

data class ProjectedLightBounds(
    val minTileX: Int,
    val maxTileX: Int,
    val minTileY: Int,
    val maxTileY: Int,
    val minSlice: Int,
    val maxSlice: Int,
)

data class ScreenSpaceLightPosition(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
)

fun extractClipPlanes(projectionMatrix: Matrix4f): ClusterClipPlanes {
    val nearPlane = projectionMatrix.m32() / (projectionMatrix.m22() - 1f)
    val farPlane = projectionMatrix.m32() / (projectionMatrix.m22() + 1f)
    return ClusterClipPlanes(
        nearPlane = nearPlane.coerceAtLeast(0.001f),
        farPlane = farPlane.coerceAtLeast(nearPlane + 0.001f),
    )
}

fun selectLogarithmicSlice(
    depth: Float,
    nearPlane: Float,
    farPlane: Float,
    slices: Int = ClusteredLightingConfig.Z_SLICES,
): Int {
    val clampedDepth = depth.coerceIn(nearPlane, farPlane)
    val ratio = ln(clampedDepth / nearPlane) / ln(farPlane / nearPlane)
    return floor(ratio * slices).toInt().coerceIn(0, slices - 1)
}

fun spotLightDirection(rotation: de.fabmax.kool.math.QuatF): Vec3f =
    Vec3f(0f, 0f, 1f).rotateBy(rotation).normed()

fun projectLightBounds(
    viewSpaceCenter: Vector3f,
    influenceRadius: Float,
    projectionMatrix: Matrix4f,
    viewWidth: Int,
    viewHeight: Int,
    nearPlane: Float,
    farPlane: Float,
    tileSize: Int = ClusteredLightingConfig.TILE_SIZE,
    slices: Int = ClusteredLightingConfig.Z_SLICES,
): ProjectedLightBounds? {
    val centerDepth = -viewSpaceCenter.z
    if (centerDepth + influenceRadius <= nearPlane || centerDepth - influenceRadius >= farPlane) return null

    val minDepth = max(nearPlane, centerDepth - influenceRadius)
    val maxDepth = min(farPlane, centerDepth + influenceRadius)
    val minSlice = selectLogarithmicSlice(minDepth, nearPlane, farPlane, slices)
    val maxSlice = selectLogarithmicSlice(maxDepth, nearPlane, farPlane, slices)

    if (centerDepth <= influenceRadius) {
        return ProjectedLightBounds(
            minTileX = 0,
            maxTileX = max(0, ceil(viewWidth / tileSize.toFloat()).toInt() - 1),
            minTileY = 0,
            maxTileY = max(0, ceil(viewHeight / tileSize.toFloat()).toInt() - 1),
            minSlice = minSlice,
            maxSlice = maxSlice,
        )
    }

    var minScreenX = Float.POSITIVE_INFINITY
    var maxScreenX = Float.NEGATIVE_INFINITY
    var minScreenY = Float.POSITIVE_INFINITY
    var maxScreenY = Float.NEGATIVE_INFINITY

    fun accumulateSample(x: Float, y: Float, z: Float): Boolean {
        val clip = projectionMatrix.transform(Vector4f(x, y, z, 1f))
        if (clip.w <= 0f) return false

        val invW = 1f / clip.w
        val ndcX = clip.x * invW
        val ndcY = clip.y * invW

        minScreenX = min(minScreenX, (ndcX * 0.5f + 0.5f) * viewWidth)
        maxScreenX = max(maxScreenX, (ndcX * 0.5f + 0.5f) * viewWidth)
        minScreenY = min(minScreenY, (1f - (ndcY * 0.5f + 0.5f)) * viewHeight)
        maxScreenY = max(maxScreenY, (1f - (ndcY * 0.5f + 0.5f)) * viewHeight)
        return true
    }

    if (!accumulateSample(viewSpaceCenter.x - influenceRadius, viewSpaceCenter.y, viewSpaceCenter.z) ||
        !accumulateSample(viewSpaceCenter.x + influenceRadius, viewSpaceCenter.y, viewSpaceCenter.z) ||
        !accumulateSample(viewSpaceCenter.x, viewSpaceCenter.y - influenceRadius, viewSpaceCenter.z) ||
        !accumulateSample(viewSpaceCenter.x, viewSpaceCenter.y + influenceRadius, viewSpaceCenter.z) ||
        !accumulateSample(viewSpaceCenter.x, viewSpaceCenter.y, viewSpaceCenter.z - influenceRadius) ||
        !accumulateSample(viewSpaceCenter.x, viewSpaceCenter.y, viewSpaceCenter.z + influenceRadius)
    ) {
        return ProjectedLightBounds(
            minTileX = 0,
            maxTileX = max(0, ceil(viewWidth / tileSize.toFloat()).toInt() - 1),
            minTileY = 0,
            maxTileY = max(0, ceil(viewHeight / tileSize.toFloat()).toInt() - 1),
            minSlice = minSlice,
            maxSlice = maxSlice,
        )
    }

    val tileCountX = max(1, ceil(viewWidth / tileSize.toFloat()).toInt())
    val tileCountY = max(1, ceil(viewHeight / tileSize.toFloat()).toInt())
    val clampedMinX = floor(minScreenX / tileSize).toInt().coerceIn(0, tileCountX - 1)
    val clampedMaxX = floor(maxScreenX / tileSize).toInt().coerceIn(0, tileCountX - 1)
    val clampedMinY = floor(minScreenY / tileSize).toInt().coerceIn(0, tileCountY - 1)
    val clampedMaxY = floor(maxScreenY / tileSize).toInt().coerceIn(0, tileCountY - 1)

    return ProjectedLightBounds(
        minTileX = min(clampedMinX, clampedMaxX),
        maxTileX = max(clampedMinX, clampedMaxX),
        minTileY = min(clampedMinY, clampedMaxY),
        maxTileY = max(clampedMinY, clampedMaxY),
        minSlice = min(minSlice, maxSlice),
        maxSlice = max(minSlice, maxSlice),
    )
}

fun projectToScreen(
    worldPosition: Vector3f,
    viewProjectionMatrix: Matrix4f,
    viewWidth: Int,
    viewHeight: Int,
): ScreenSpaceLightPosition? {
    val clip = viewProjectionMatrix.transform(Vector4f(worldPosition, 1f))
    if (clip.w == 0f) return null

    val invW = 1f / clip.w
    val ndcX = clip.x * invW
    val ndcY = clip.y * invW
    val ndcZ = clip.z * invW

    return ScreenSpaceLightPosition(
        x = (ndcX * 0.5f + 0.5f) * viewWidth,
        y = (1f - (ndcY * 0.5f + 0.5f)) * viewHeight,
        z = ndcZ,
        w = clip.w,
    )
}

fun hasClusteredLightingFeatureFlag(requiredFlags: List<String>, optionalFlags: List<String>): Boolean =
    requiredFlags.contains(ClusteredLightingConfig.FEATURE_FLAG) || optionalFlags.contains(ClusteredLightingConfig.FEATURE_FLAG)

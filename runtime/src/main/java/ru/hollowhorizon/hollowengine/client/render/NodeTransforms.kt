package ru.hollowhorizon.hollowengine.client.render

import de.fabmax.kool.math.*
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.models.internal.v2.calculateBounds
import ru.hollowhorizon.hollowengine.client.utils.math.rotateBy
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import kotlin.math.abs
import kotlin.math.max

data class ResolvedNodeTransform(
    val transform: TrsTransformF,
    val light: Int,
) {
    val position: Vec3
        get() = Vec3(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble(),
        )
}

fun resolveNodeTransform(
    level: net.minecraft.world.level.Level,
    hostUuid: java.util.UUID?,
    transform: TransformComponent,
    partialTick: Float,
): ResolvedNodeTransform? {
    val worldTransform = if (hostUuid == null) {
        TrsTransformF().set(transform.transform)
    } else {
        val host = findNodeHostEntity(level, hostUuid) ?: return null
        val hostYaw = when (host) {
            is LivingEntity -> Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
            else -> Mth.rotLerp(partialTick, host.yRotO, host.yRot)
        }
        val hostPosition = Vec3f(
            Mth.lerp(partialTick.toDouble(), host.xOld, host.x).toFloat(),
            Mth.lerp(partialTick.toDouble(), host.yOld, host.y).toFloat(),
            Mth.lerp(partialTick.toDouble(), host.zOld, host.z).toFloat(),
        )
        val hostRotation = QuatF((-hostYaw).deg, Vec3f.Y_AXIS)
        val local = transform.transform
        val worldTranslation = Vec3f(local.translation).rotateBy(hostRotation) + hostPosition
        val worldRotation = MutableQuatF(hostRotation).mul(local.rotation).norm()
        TrsTransformF().setCompositionOf(
            worldTranslation,
            worldRotation,
            Vec3f(local.scale),
        )
    }

    return ResolvedNodeTransform(
        transform = worldTransform,
        light = LevelRenderer.getLightColor(level, BlockPos.containing(worldTransform.translation.x.toDouble(), worldTransform.translation.y.toDouble(), worldTransform.translation.z.toDouble())),
    )
}

fun buildNodeRenderBounds(model: Model, transform: TrsTransformF, modelScale: Float): AABB {
    val localBounds = model.attachment.calculateBounds()
    val worldTransform = TrsTransformF().set(transform)
    worldTransform.scale(Vec3f(modelScale, modelScale, modelScale))

    if (localBounds == null) {
        val maxAxis = max(max(abs(worldTransform.scale.x), abs(worldTransform.scale.y)), abs(worldTransform.scale.z)).toDouble()
        val position = worldTransform.translation
        return AABB(
            position.x - maxAxis,
            position.y - maxAxis,
            position.z - maxAxis,
            position.x + maxAxis,
            position.y + maxAxis,
            position.z + maxAxis,
        )
    }

    val min = localBounds.first
    val max = localBounds.second
    val matrix = worldTransform.matrixF
    val source = MutableVec3f()
    val transformed = MutableVec3f()
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    fun update(x: Float, y: Float, z: Float) {
        source.set(x, y, z)
        matrix.transform(source, 1f, transformed)
        minX = kotlin.math.min(minX, transformed.x)
        minY = kotlin.math.min(minY, transformed.y)
        minZ = kotlin.math.min(minZ, transformed.z)
        maxX = kotlin.math.max(maxX, transformed.x)
        maxY = kotlin.math.max(maxY, transformed.y)
        maxZ = kotlin.math.max(maxZ, transformed.z)
    }

    update(min.x, min.y, min.z)
    update(min.x, min.y, max.z)
    update(min.x, max.y, min.z)
    update(min.x, max.y, max.z)
    update(max.x, min.y, min.z)
    update(max.x, min.y, max.z)
    update(max.x, max.y, min.z)
    update(max.x, max.y, max.z)

    return AABB(
        minX.toDouble(),
        minY.toDouble(),
        minZ.toDouble(),
        maxX.toDouble(),
        maxY.toDouble(),
        maxZ.toDouble(),
    )
}

fun worldTransformToComponent(
    level: net.minecraft.world.level.Level,
    hostUuid: java.util.UUID?,
    worldPosition: Vec3,
    worldRotation: QuatF,
    worldScale: Vec3f,
    partialTick: Float,
): TransformComponent? {
    val normalizedScale = sanitizeScale(worldScale)
    return if (hostUuid == null) {
        TransformComponent(
            translation = Vec3f(worldPosition.x.toFloat(), worldPosition.y.toFloat(), worldPosition.z.toFloat()),
            rotation = QuatF(worldRotation),
            scale = normalizedScale,
        )
    } else {
        val host = findNodeHostEntity(level, hostUuid) ?: return null
        val hostYaw = when (host) {
            is LivingEntity -> Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
            else -> Mth.rotLerp(partialTick, host.yRotO, host.yRot)
        }
        val hostPosition = Vec3f(
            Mth.lerp(partialTick.toDouble(), host.xOld, host.x).toFloat(),
            Mth.lerp(partialTick.toDouble(), host.yOld, host.y).toFloat(),
            Mth.lerp(partialTick.toDouble(), host.zOld, host.z).toFloat(),
        )
        val hostTranslationRotation = QuatF((-hostYaw).deg, Vec3f.Y_AXIS)
        val inverseHostTranslationRotation = MutableQuatF(hostTranslationRotation).invert().norm()
        val hostRotation = QuatF(hostYaw.deg, Vec3f.Y_AXIS)
        val inverseHostRotation = MutableQuatF(hostRotation).invert().norm()
        val localTranslation = Vec3f(
            (worldPosition.x - hostPosition.x).toFloat(),
            (worldPosition.y - hostPosition.y).toFloat(),
            (worldPosition.z - hostPosition.z).toFloat(),
        ).rotateBy(inverseHostTranslationRotation)
        val localRotation = MutableQuatF(inverseHostRotation).mul(worldRotation).norm()
        TransformComponent(
            translation = localTranslation,
            rotation = localRotation,
            scale = normalizedScale,
        )
    }
}

fun quatFToGizmoRotation(rotation: QuatF): QuatD =
    MutableQuatD(rotation.x.toDouble(), rotation.y.toDouble(), rotation.z.toDouble(), rotation.w.toDouble()).norm()

fun gizmoRotationToQuatF(rotation: QuatD): QuatF =
    MutableQuatF(rotation.x.toFloat(), rotation.y.toFloat(), rotation.z.toFloat(), rotation.w.toFloat()).norm()

fun findNodeHostEntity(level: net.minecraft.world.level.Level, hostUuid: java.util.UUID): MCEntity? {
    if (level is ServerLevel) return level.getEntity(hostUuid)
    if (level is ClientLevel) {
        level.entitiesForRendering().forEach { entity ->
            if (entity.uuid == hostUuid) return entity
        }
    }
    return null
}

private fun sanitizeScale(scale: Vec3f): Vec3f =
    Vec3f(
        sanitizeScaleComponent(scale.x),
        sanitizeScaleComponent(scale.y),
        sanitizeScaleComponent(scale.z),
    )

private fun sanitizeScaleComponent(value: Float): Float {
    val normalized = if (abs(value) < 0.01f) 0.01f else value
    return if (normalized == -0.0f) 0.01f else normalized
}

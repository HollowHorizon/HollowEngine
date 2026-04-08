package ru.hollowhorizon.hollowengine.client.render

import com.mineinabyss.geary.modules.get
import de.fabmax.kool.math.EulerOrder
import de.fabmax.kool.math.MutableQuatD
import de.fabmax.kool.math.MutableVec3d
import de.fabmax.kool.math.QuatD
import de.fabmax.kool.math.Vec3d
import de.fabmax.kool.math.rotateByEulers
import de.fabmax.kool.math.toEulers
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.models.internal.v2.calculateBounds
import ru.hollowhorizon.hollowengine.common.geary.anchor.AnchorComponent
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.anchor.WorldAnchor
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class ResolvedAnchorTransform(
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
    val scale: Float,
    val light: Int,
)

fun resolveAnchoredTransform(
    level: net.minecraft.world.level.Level,
    anchor: AnchorComponent,
    transform: TransformComponent,
    partialTick: Float,
): ResolvedAnchorTransform? {
    val host = (anchor as? EntityAnchor)?.let { findAnchorHostEntity(level, it.hostUuid) }
    val hostYaw = when (host) {
        is LivingEntity -> Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
        null -> 0f
        else -> Mth.rotLerp(partialTick, host.yRotO, host.yRot)
    }
    val hostPosition = host?.let {
        Vec3(
            Mth.lerp(partialTick.toDouble(), it.xOld, it.x),
            Mth.lerp(partialTick.toDouble(), it.yOld, it.y),
            Mth.lerp(partialTick.toDouble(), it.zOld, it.z),
        )
    }

    val position = when (anchor) {
        is WorldAnchor -> Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble())
        is EntityAnchor -> {
            val resolvedHostPosition = hostPosition ?: return null
            val rotatedOffset = rotateAroundY(
                Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble()),
                -hostYaw * Mth.DEG_TO_RAD,
            )
            resolvedHostPosition.add(rotatedOffset)
        }
    }

    val yaw = when (anchor) {
        is EntityAnchor -> hostYaw + transform.yaw
        is WorldAnchor -> transform.yaw
    }

    return ResolvedAnchorTransform(
        position = position,
        yaw = yaw,
        pitch = transform.pitch,
        scale = transform.scale,
        light = LevelRenderer.getLightColor(level, BlockPos.containing(position)),
    )
}

fun buildAnchoredRenderBounds(model: Model, position: Vec3, scale: Float): AABB {
    val localBounds = model.attachment.calculateBounds()
    if (localBounds == null) {
        return AABB(
            position.x - scale.toDouble(),
            position.y - scale.toDouble(),
            position.z - scale.toDouble(),
            position.x + scale.toDouble(),
            position.y + scale.toDouble(),
            position.z + scale.toDouble(),
        )
    }

    val min = localBounds.first
    val max = localBounds.second
    return AABB(
        position.x + min.x * scale,
        position.y + min.y * scale,
        position.z + min.z * scale,
        position.x + max.x * scale,
        position.y + max.y * scale,
        position.z + max.z * scale,
    )
}

fun worldTransformToComponent(
    level: net.minecraft.world.level.Level,
    anchor: AnchorComponent,
    worldPosition: Vec3,
    worldYaw: Float,
    worldPitch: Float,
    worldScale: Float,
    partialTick: Float,
): TransformComponent? {
    val normalizedScale = max(worldScale, 0.01f)
    return when (anchor) {
        is WorldAnchor -> TransformComponent(
            x = worldPosition.x.toFloat(),
            y = worldPosition.y.toFloat(),
            z = worldPosition.z.toFloat(),
            yaw = normalizeYaw(worldYaw),
            pitch = worldPitch,
            scale = normalizedScale,
        )
        is EntityAnchor -> {
            val host = findAnchorHostEntity(level, anchor.hostUuid) ?: return null
            val hostYaw = when (host) {
                is LivingEntity -> Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
                else -> Mth.rotLerp(partialTick, host.yRotO, host.yRot)
            }
            val hostPosition = Vec3(
                Mth.lerp(partialTick.toDouble(), host.xOld, host.x),
                Mth.lerp(partialTick.toDouble(), host.yOld, host.y),
                Mth.lerp(partialTick.toDouble(), host.zOld, host.z),
            )
            val offset = rotateAroundY(worldPosition.subtract(hostPosition), hostYaw * Mth.DEG_TO_RAD)
            TransformComponent(
                x = offset.x.toFloat(),
                y = offset.y.toFloat(),
                z = offset.z.toFloat(),
                yaw = normalizeYaw(worldYaw - hostYaw),
                pitch = worldPitch,
                scale = normalizedScale,
            )
        }
    }
}

fun yawPitchToGizmoRotation(yaw: Float, pitch: Float): QuatD =
    MutableQuatD().rotateByEulers(Vec3d(pitch.toDouble(), yaw.toDouble(), 0.0), EulerOrder.YXZ)

fun gizmoRotationToYawPitch(rotation: QuatD): Pair<Float, Float> {
    val eulers = rotation.toEulers(MutableVec3d(), EulerOrder.YXZ)
    return eulers.y.toFloat() to eulers.x.toFloat()
}

fun findAnchorHostEntity(level: net.minecraft.world.level.Level, hostUuid: java.util.UUID): MCEntity? {
    val runtimeId = MaterializationRuntimeState.service(level).runtimeIdOf(hostUuid) ?: return null
    return with(level.geary) { runtimeId.toGeary().get<MCEntity>() }
}

fun rotateAroundY(vector: Vec3, yawRadians: Float): Vec3 {
    val cos = cos(yawRadians)
    val sin = sin(yawRadians)
    return Vec3(
        vector.x * cos - vector.z * sin,
        vector.y,
        vector.x * sin + vector.z * cos,
    )
}

private fun normalizeYaw(yaw: Float): Float {
    val normalized = yaw % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

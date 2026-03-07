package ru.hollowhorizon.hollowengine.common.geary.components.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.EditorName
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import java.util.UUID

private val EMPTY_UUID: UUID = UUID(0L, 0L)

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class EntityReference(
    @EditorName("UUID")
    val uuid: @Serializable(ForUuid::class) UUID = EMPTY_UUID,
    @EditorName("Dimension")
    val level: @Serializable(ForResourceLocation::class) ResourceLocation = Level.OVERWORLD.location(),
) {
    fun isEmpty(): Boolean = uuid == EMPTY_UUID

    fun resolve(origin: Level): Entity? {
        if (isEmpty()) return null
        val targetLevel: ServerLevel = if (origin.dimension().location() == level) {
            origin as? ServerLevel ?: return null
        } else {
            origin.server?.getLevel(ResourceKey.create(Registries.DIMENSION, level)) ?: return null
        }
        return targetLevel.getEntity(uuid)
    }
}

enum class LookTargetMode {
    ENTITY,
    POSITION,
}

@Registerable
@Serializable
@SerialName("hollowengine:look_at_target")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class LookAtTargetComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Target mode")
    val targetMode: LookTargetMode = LookTargetMode.ENTITY,
    @EditorName("Entity target")
    val targetEntity: EntityReference = EntityReference(),
    @EditorName("Position target")
    val targetPosition: @Serializable(ForVec3::class) Vec3 = Vec3.ZERO,
    @EditorName("Yaw speed")
    val yawSpeed: Float = 10f,
    @EditorName("Pitch speed")
    val pitchSpeed: Float = 10f,
)

@Registerable
@Serializable
@SerialName("hollowengine:follow_target")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class FollowTargetComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Target entity")
    val targetEntity: EntityReference = EntityReference(),
    @EditorName("Speed")
    val speed: Float = 1f,
    @EditorName("Preferred distance")
    val preferredDistance: Float = 2f,
    @EditorName("Max distance")
    val maxDistance: Float = 24f,
)

@Registerable
@Serializable
@SerialName("hollowengine:move_to_position")
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class MoveToPositionComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Target position")
    val target: @Serializable(ForVec3::class) Vec3 = Vec3.ZERO,
    @EditorName("Speed")
    val speed: Float = 1f,
    @EditorName("Arrival radius")
    val arrivalRadius: Float = 1.5f,
    @EditorName("Stop on arrival")
    val stopOnArrival: Boolean = true,
)

@Registerable
@Serializable
@SerialName("hollowengine:attack_target")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class AttackTargetComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Target entity")
    val targetEntity: EntityReference = EntityReference(),
    @EditorName("Attack range")
    val attackRange: Float = 2f,
    @EditorName("Chase range")
    val chaseRange: Float = 16f,
    @EditorName("Forget distance")
    val forgetDistance: Float = 32f,
    @EditorName("Cooldown ticks")
    val cooldownTicks: Int = 20,
)

@Registerable
@Serializable
@SerialName("hollowengine:pickup_loot")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class PickupLootComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Radius")
    val radius: Float = 6f,
    @EditorName("Scan interval ticks")
    val scanIntervalTicks: Int = 10,
)

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class PatrolPoint(
    @EditorName("Position")
    val position: @Serializable(ForVec3::class) Vec3 = Vec3.ZERO,
    @EditorName("Wait ticks")
    val waitTicks: Int = 0,
    @EditorName("Look at next point")
    val lookAtNextPoint: Boolean = true,
)

@Registerable
@Serializable
@SerialName("hollowengine:patrol_path")
@EditorIcon("hollowengine:textures/gui/icons/folder_npcs.svg")
data class PatrolPathComponent(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Points")
    val points: List<PatrolPoint> = emptyList(),
    @EditorName("Loop")
    val loop: Boolean = true,
    @EditorName("Speed")
    val speed: Float = 1f,
    @EditorName("Arrival radius")
    val arrivalRadius: Float = 1.5f,
)

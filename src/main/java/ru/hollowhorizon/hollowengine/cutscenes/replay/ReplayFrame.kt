package ru.hollowhorizon.hollowengine.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import java.util.*

@Serializable
data class ReplayFrame(
    // Position
    val x: Double,
    val y: Double,
    val z: Double,

    // Rotation
    val yaw: Float,
    val headYaw: Float,
    val pitch: Float,

    // Motion
    val motionX: Double,
    val motionY: Double,
    val motionZ: Double,

    // Utils
    val isSneaking: Boolean,
    val isSprinting: Boolean,
    val isSwinging: Boolean,
    val pose: Pose,

    //animations
    val anim: RecordingContainer? = null,

    //World & Inventory
    val brokenBlocks: HashSet<ReplayBlock> = HashSet(),
    val placedBlocks: HashSet<ReplayBlock> = HashSet(),
    val usedBlocks: HashSet<ReplayBlock> = HashSet(),

    val armorAndWeapon: Map<EquipmentSlot, ReplayItem> = EnumMap(EquipmentSlot::class.java),
) {
    fun apply(entity: LivingEntity, fakePlayer: FakePlayer) {
        entity.setPos(x, y, z)
        entity.yRot = yaw
        entity.yHeadRot = headYaw
        entity.xRot = pitch
        entity.setDeltaMovement(motionX, motionY, motionZ)
        entity.isShiftKeyDown = isSneaking
        entity.isSprinting = isSprinting
        entity.pose = pose
        entity.swinging = isSwinging

        armorAndWeapon.forEach {
            entity.setItemSlot(it.key, it.value.toStack())
        }

        anim?.let { anim ->
            var name = anim.animation

            val serverLayers = entity[AnimatedEntityCapability::class].layers

            if(name.startsWith("%STOP%")) {
                name = name.substring(6)

                serverLayers.removeIfNoUpdate { it.animation == name }
                StopAnimationPacket(entity.id, name).send(PacketDistributor.TRACKING_ENTITY.with { entity })

                return@let
            }

            if (serverLayers.any { it.animation == name }) return@let

            StartAnimationPacket(
                entity.id,
                anim.animation,
                anim.layerMode,
                anim.playMode,
                anim.speed
            ).send(PacketDistributor.TRACKING_ENTITY.with { entity })

            if (anim.playMode != PlayMode.ONCE) {
                //Нужно на случай если клиентская сущность выйдет из зоны прогрузки (удалится)
                serverLayers.addNoUpdate(
                    AnimationLayer(
                        anim.animation,
                        anim.layerMode,
                        anim.playMode,
                        anim.speed
                    )
                )
            }
        }

        val level = entity.level
        brokenBlocks.forEach { it.destroy(fakePlayer) }
        placedBlocks.forEach { it.place(level, entity, fakePlayer) }
        usedBlocks.forEach { it.use(level, entity, fakePlayer) }
    }

    companion object {
        fun loadFromPlayer(
            recorder: ReplayRecorder, entity: LivingEntity, animationFrame: RecordingContainer?
        ): ReplayFrame {
            return ReplayFrame(
                entity.x,
                entity.y,
                entity.z,
                entity.yRot,
                entity.yHeadRot,
                entity.xRot,
                entity.deltaMovement.x,
                entity.deltaMovement.y,
                entity.deltaMovement.z,
                entity.isShiftKeyDown,
                entity.isSprinting,
                entity.swinging,
                entity.pose,
                animationFrame,

                recorder.brokenBlocks.toHashSet(),
                recorder.placedBlocks.toHashSet(),
                recorder.usedBlocks.toHashSet(),

                entity.saveItems()
            )
        }
    }
}

private fun LivingEntity.saveItems(): Map<EquipmentSlot, ReplayItem> {
    val map: MutableMap<EquipmentSlot, ReplayItem> = EnumMap(EquipmentSlot::class.java)
    EquipmentSlot.entries.forEach {
        val stack = getItemBySlot(it)
        if (!stack.isEmpty) {
            map[it] = ReplayItem(stack)
        }
    }
    return map
}

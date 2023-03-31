package ru.hollowhorizon.hollowengine.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.Pose
import net.minecraft.inventory.EquipmentSlotType
import net.minecraftforge.common.util.FakePlayer
import java.util.*
import kotlin.collections.HashSet

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
    val pose: Pose,

    //World & Inventory
    val brokenBlocks: HashSet<ReplayBlock> = HashSet(),
    val placedBlocks: HashSet<ReplayBlock> = HashSet(),
    val usedBlocks: HashSet<ReplayBlock> = HashSet(),

    val armorAndWeapon: Map<EquipmentSlotType, ReplayItem> = EnumMap(net.minecraft.inventory.EquipmentSlotType::class.java),
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

        armorAndWeapon.forEach {
            entity.setItemSlot(it.key, it.value.toStack())
        }

        val level = entity.level
        brokenBlocks.forEach { it.destroy(fakePlayer) }
        placedBlocks.forEach { it.place(level, entity, fakePlayer) }
        usedBlocks.forEach { it.use(level, entity, fakePlayer) }
    }

    companion object {
        fun loadFromPlayer(recorder: ReplayRecorder, entity: LivingEntity): ReplayFrame {
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
                entity.pose,

                recorder.brokenBlocks.toHashSet(),
                recorder.placedBlocks.toHashSet(),
                recorder.usedBlocks.toHashSet(),

                entity.saveItems()
            )
        }
    }
}

private fun LivingEntity.saveItems(): Map<EquipmentSlotType, ReplayItem> {
    val map: MutableMap<EquipmentSlotType, ReplayItem> = EnumMap(net.minecraft.inventory.EquipmentSlotType::class.java)
    EquipmentSlotType.values().forEach {
        val stack = getItemBySlot(it)
        if(!stack.isEmpty) {
            map[it] = ReplayItem(stack)
        }
    }
    return map
}

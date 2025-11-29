package ru.hollowhorizon.hollowengine.common.utils.molang.runtime

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.FlyingAnimal
import net.minecraft.world.level.block.entity.BlockEntity
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.controller.MOVEMENT_FACTOR
import ru.hollowhorizon.hollowengine.client.models.internal.controller.calculateSpeedViaDeltaMovement
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.abs

interface Query {
    val ground_speed: Float get() = 0f
    val is_moving: Boolean get() = false
    val is_sneaking: Boolean get() = false
    val is_sprinting: Boolean get() = false
    val is_jumping: Boolean get() = false
    val velocity_y: Float get() = 0f
    val velocity_x: Float get() = 0f
    val velocity_z: Float get() = 0f
    val is_flying: Boolean get() = false
    val fall_ticks: Float get() = 0f
    val is_swimming: Boolean get() = false
    val is_sitting: Boolean get() = false
    val is_sleeping: Boolean get() = false
    val is_hurt: Boolean get() = false
    val is_swinging: Boolean get() = false
    val is_alive: Boolean get() = true
    val is_on_ground: Boolean get() = true
    val head_x_rotation: Float get() = 0f
    val head_y_rotation: Float get() = 0f
    val anim_time: Float get() = 0f
    val life_time: Float get() = 0f
    val modified_distance_moved: Float get() = 0f
    val modified_move_speed: Float get() = 0f

    companion object {
        val EMPTY = object : Query {}
        val GLFW_TIME = object : Query {
            override val anim_time: Float
                get() = GLFW.glfwGetTime().toFloat()
        }
    }
}

fun ComponentDispatcher.createQuery() = when (this) {
    is LivingEntity -> LivingEntityQuery(this)
    is BlockEntity -> BlockEntityQuery(this)
    else -> error("Dispatcher $this not supported yet!")
}

class BlockEntityQuery(val blockEntity: BlockEntity) : Query {
    private val startTime = anim_time

    override val anim_time: Float get() = TickHandler.time
    override val life_time: Float get() = anim_time - startTime
}

class LivingEntityQuery(val entity: LivingEntity) : Query {
    override val ground_speed: Float get() = calculateSpeedViaDeltaMovement(entity)
    override val is_moving: Boolean get() = abs(ground_speed) >= MOVEMENT_FACTOR
    override val is_sneaking: Boolean get() = entity.isShiftKeyDown
    override val is_sprinting: Boolean get() = entity.isSprinting
    override val is_jumping: Boolean get() = entity.jumping
    override val velocity_x: Float get() = entity.deltaMovement.x.toFloat()
    override val velocity_y: Float get() = entity.deltaMovement.y.toFloat()
    override val velocity_z: Float get() = entity.deltaMovement.z.toFloat()
    override val is_flying: Boolean get() = entity is FlyingAnimal && entity.isFlying
    override val fall_ticks: Float get() = entity.fallFlyingTicks.toFloat()
    override val is_swimming: Boolean get() = entity.isSwimming
    override val is_sitting: Boolean get() = entity.vehicle != null
    override val is_sleeping: Boolean get() = entity.isSleeping
    override val is_hurt: Boolean get() = entity.hurtTime > 0
    override val is_swinging: Boolean get() = entity.swinging
    override val is_alive: Boolean get() = entity.isAlive
    override val is_on_ground: Boolean get() = entity.onGround()
    override val head_x_rotation: Float get() = entity.xRot
    override val head_y_rotation: Float get() = entity.yHeadRot
    override val anim_time: Float get() = entity.tickCount + TickHandler.partialTick
    override val life_time: Float get() = entity.tickCount.toFloat()
    override val modified_distance_moved: Float get() = 0f
    override val modified_move_speed: Float get() = ground_speed
}
package ru.hollowhorizon.hollowengine.client.models.internal.animator

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * The variables animation expressions are evaluated against.
 */
fun entityAnimationVariables(entity: Entity?, partialTick: Float): AnimatorEvaluationContext {
    val baseLevelTime = Minecraft.getInstance().level?.gameTime?.toFloat() ?: 0f
    val levelTime = baseLevelTime + partialTick
    val time = (entity?.tickCount?.toFloat() ?: baseLevelTime) + partialTick

    val values = LinkedHashMap<String, Float>(EXPECTED_VARIABLE_COUNT)
    values["partial_tick"] = partialTick
    values["game_time"] = levelTime
    values["life_time"] = time
    values["age"] = time

    if (entity != null) putEntityVariables(values, entity, partialTick)
    if (entity is LivingEntity) putLivingVariables(values, entity, partialTick)

    return AnimatorEvaluationContext(deltaTime = 0f, time = time, values = values)
}

private fun putEntityVariables(values: MutableMap<String, Float>, entity: Entity, partialTick: Float) {
    val velocity = entity.deltaMovement
    val movementYaw = when (entity) {
        is LivingEntity -> Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
        else -> Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
    }
    val localForwardSpeed = localForwardSpeed(velocity, movementYaw)
    val localSideSpeed = localSideSpeed(velocity, movementYaw)
    val rawHorizontalSpeed = velocity.horizontalDistance().toFloat() * 20f
    val signedLocomotionSpeed = signedLocomotionSpeed(rawHorizontalSpeed, localForwardSpeed)

    values["entity_id"] = entity.id.toFloat()
    values["is_alive"] = entity.isAlive.asFloat()
    values["is_on_ground"] = entity.onGround().asFloat()
    values["velocity_x"] = velocity.x.toFloat()
    values["velocity_y"] = velocity.y.toFloat()
    values["velocity_z"] = velocity.z.toFloat()
    values["horizontal_speed"] = rawHorizontalSpeed
    // Blocks per tick, while horizontal_speed above is blocks per second.
    values["local_forward_speed"] = localForwardSpeed
    values["local_side_speed"] = localSideSpeed
    values["signed_horizontal_speed"] = signedLocomotionSpeed
    values["movement_animation_speed"] = signedLocomotionSpeed * MOVEMENT_ANIMATION_SPEED_SCALE
    values["fall_distance"] = entity.fallDistance
    values["is_in_water"] = entity.isInWater.asFloat()
    values["is_under_water"] = entity.isUnderWater.asFloat()
    values["is_in_lava"] = entity.isInLava.asFloat()
    values["is_on_fire"] = entity.isOnFire.asFloat()
    values["remaining_fire_ticks"] = entity.remainingFireTicks.toFloat()
    values["is_crouching"] = entity.isCrouching.asFloat()
    values["is_swimming"] = entity.isSwimming.asFloat()
    values["is_visually_swimming"] = entity.isVisuallySwimming.asFloat()
    values["is_visually_crawling"] = entity.isVisuallyCrawling.asFloat()
    values["is_invisible"] = entity.isInvisible.asFloat()
    values["is_glowing"] = entity.isCurrentlyGlowing.asFloat()
    values["is_passenger"] = entity.isPassenger.asFloat()
    values["is_vehicle"] = entity.isVehicle.asFloat()
    values["is_no_gravity"] = entity.isNoGravity.asFloat()
    values["is_in_wall"] = entity.isInWall.asFloat()
    values["is_shift_key_down"] = entity.isShiftKeyDown.asFloat()
    values["eye_height"] = entity.eyeHeight
    values["bbox_width"] = entity.bbWidth
    values["bbox_height"] = entity.bbHeight
    values["horizontal_collision"] = entity.horizontalCollision.asFloat()
    values["vertical_collision"] = entity.verticalCollision.asFloat()
    values["vertical_collision_below"] = entity.verticalCollisionBelow.asFloat()
    values["move_dist"] = entity.moveDist
    values["fly_dist"] = entity.flyDist
    values["invulnerable_time"] = entity.invulnerableTime.toFloat()
    values["ticks_frozen"] = entity.ticksFrozen.toFloat()
    values["percent_frozen"] = entity.percentFrozen
    values["is_fully_frozen"] = entity.isFullyFrozen.asFloat()
    values["air_supply"] = entity.airSupply.toFloat()
    values["max_air_supply"] = entity.maxAirSupply.toFloat()
    values["has_custom_name"] = entity.hasCustomName().asFloat()
}

private fun putLivingVariables(values: MutableMap<String, Float>, entity: LivingEntity, partialTick: Float) {
    val bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
    val headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot)
    val headPitch = Mth.lerp(partialTick, entity.xRotO, entity.xRot)

    values["hurt_time"] = entity.hurtTime.toFloat()
    values["head_y_rotation"] = headYaw
    values["head_x_rotation"] = headPitch
    values["body_y_rotation"] = bodyYaw
    values["head_body_y_delta"] = Mth.wrapDegrees(headYaw - bodyYaw)
    values["walk_animation_speed"] = entity.walkAnimation.speed(partialTick)
    values["walk_animation_position"] = entity.walkAnimation.position(partialTick)
    values["is_sprinting"] = entity.isSprinting.asFloat()
    values["is_sneaking"] = entity.isShiftKeyDown.asFloat()
    values["health"] = entity.health
    values["max_health"] = entity.maxHealth
    values["health_ratio"] = entity.health / entity.maxHealth.coerceAtLeast(1f)
    values["is_dead_or_dying"] = entity.isDeadOrDying.asFloat()
    values["death_time"] = entity.deathTime.toFloat()
    values["death_progress"] = entity.deathTime.toFloat() / LivingEntity.DEATH_DURATION.toFloat()
    values["hurt_duration"] = entity.hurtDuration.toFloat()
    values["armor_value"] = entity.armorValue.toFloat()
    values["absorption_amount"] = entity.absorptionAmount
    values["max_absorption"] = entity.maxAbsorption
    values["is_using_item"] = entity.isUsingItem.asFloat()
    values["use_item_remaining_ticks"] = entity.useItemRemainingTicks.toFloat()
    values["ticks_using_item"] = entity.ticksUsingItem.toFloat()
    values["is_blocking"] = entity.isBlocking.asFloat()
    values["attack_anim"] = entity.getAttackAnim(partialTick)
    values["swing_time"] = entity.swingTime.toFloat()
    values["is_swinging"] = entity.swinging.asFloat()
    values["swim_amount"] = entity.getSwimAmount(partialTick)
    values["is_fall_flying"] = entity.isFallFlying.asFloat()
    values["fall_flying_ticks"] = entity.fallFlyingTicks.toFloat()
    values["is_autospin_attack"] = entity.isAutoSpinAttack.asFloat()
    values["is_climbing"] = entity.onClimbable().asFloat()
    values["is_sleeping"] = entity.isSleeping.asFloat()
    values["no_jump_delay"] = entity.noJumpDelay.toFloat()
    values["jump_boost_power"] = entity.jumpBoostPower
    values["speed"] = entity.speed
    values["is_sensitive_to_water"] = entity.isSensitiveToWater.asFloat()
}

internal fun localForwardSpeed(velocity: Vec3, yaw: Float): Float {
    val yawRad = yaw * Mth.DEG_TO_RAD
    return velocity.x.toFloat() * -Mth.sin(yawRad) + velocity.z.toFloat() * Mth.cos(yawRad)
}

internal fun localSideSpeed(velocity: Vec3, yaw: Float): Float {
    val yawRad = yaw * Mth.DEG_TO_RAD
    return velocity.x.toFloat() * Mth.cos(yawRad) + velocity.z.toFloat() * Mth.sin(yawRad)
}

internal fun signedLocomotionSpeed(horizontalSpeed: Float, localForwardSpeed: Float): Float =
    if (abs(localForwardSpeed) > LOCAL_MOVEMENT_EPSILON && localForwardSpeed < 0f) -horizontalSpeed
    else horizontalSpeed

private fun Boolean.asFloat(): Float = if (this) 1f else 0f

private const val LOCAL_MOVEMENT_EPSILON = 0.001f
private const val MOVEMENT_ANIMATION_SPEED_SCALE = 1f

/** Enough buckets for every variable above, so the map never rehashes while it is being filled. */
private const val EXPECTED_VARIABLE_COUNT = 128

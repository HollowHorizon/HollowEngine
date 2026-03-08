package ru.hollowhorizon.hollowengine.common.geary.components.ai

import com.mineinabyss.geary.modules.observe
import com.mineinabyss.geary.observers.events.OnRemove
import com.mineinabyss.geary.systems.query.query
import kotlinx.serialization.Serializable
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.item.ItemEntity
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.geary.GearyInitializeEvent
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.npcs.navigation.faceTowards
import ru.hollowhorizon.hollowengine.common.npcs.navigation.moveTowards
import java.util.UUID

private object AIRuntimeState {
    val attackCooldowns = hashMapOf<UUID, Int>()
    val patrolIndices = hashMapOf<UUID, Int>()
    val patrolWaits = hashMapOf<UUID, Int>()
    val lootScanCooldowns = hashMapOf<UUID, Int>()

    fun cleanup(uuid: UUID) {
        attackCooldowns.remove(uuid)
        patrolIndices.remove(uuid)
        patrolWaits.remove(uuid)
        lootScanCooldowns.remove(uuid)
    }
}

@SubscribeEvent
fun GearyInitializeEvent.initAiBehaviorSystems(): Unit = with(geary) {
    system(query<MCEntity, AttackTargetComponent>())
        .named("AttackTargetSystem")
        .exec { (mcEntity, attack) ->
            if (!attack.enabled) return@exec
            val mob = mcEntity as? Mob ?: return@exec
            val target = attack.targetEntity.resolve(mcEntity.level()) as? LivingEntity ?: return@exec
            if (!target.isAlive || target.isRemoved) {
                mob.target = null
                (mob as? PathfinderMob)?.navigation?.stop()
                return@exec
            }

            val distance = mcEntity.distanceTo(target)
            if (distance > attack.forgetDistance) {
                mob.target = null
                (mob as? PathfinderMob)?.navigation?.stop()
                return@exec
            }

            mob.target = target
            mob.faceTowards(target)

            val cooldown = AIRuntimeState.attackCooldowns.getOrDefault(mcEntity.uuid, 0)
            if (cooldown > 0) {
                AIRuntimeState.attackCooldowns[mcEntity.uuid] = cooldown - 1
            }

            if (distance > attack.attackRange) {
                if (distance <= attack.chaseRange) {
                    (mob as? PathfinderMob)?.moveTowards(target, 1.0, attack.attackRange.toDouble())
                } else {
                    (mob as? PathfinderMob)?.navigation?.stop()
                }
                return@exec
            }

            (mob as? PathfinderMob)?.navigation?.stop()
            if (cooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND)
                mob.doHurtTarget(target)
                AIRuntimeState.attackCooldowns[mcEntity.uuid] = attack.cooldownTicks.coerceAtLeast(1)
            }
        }

    system(query<MCEntity, FollowTargetComponent, AttackTargetComponent?>())
        .named("FollowTargetSystem")
        .exec { (mcEntity, follow, attack) ->
            if (!follow.enabled) return@exec
            if (attack?.enabled == true) return@exec
            val mob = mcEntity as? PathfinderMob ?: return@exec
            val target = follow.targetEntity.resolve(mcEntity.level()) ?: return@exec
            if (target.isRemoved) return@exec

            val distance = mcEntity.distanceTo(target)
            if (distance > follow.maxDistance) {
                mob.navigation.stop()
                return@exec
            }

            if (distance > follow.preferredDistance) {
                mob.moveTowards(target, follow.speed.toDouble(), follow.preferredDistance.toDouble())
            } else {
                mob.navigation.stop()
            }
        }

    system(query<MCEntity, MoveToPositionComponent>())
        .named("MoveToPositionSystem")
        .exec { (mcEntity, move) ->
            if (!move.enabled) return@exec
            val mob = mcEntity as? PathfinderMob ?: return@exec
            if (mob.moveTowards(move.target, move.speed.toDouble(), move.arrivalRadius.toDouble())) {
                if (move.stopOnArrival) {
                    mcEntity.entity.remove(MoveToPositionComponent::class)
                }
            }
        }

    system(query<MCEntity, PatrolPathComponent, MoveToPositionComponent?>())
        .named("PatrolPathSystem")
        .exec { (mcEntity, patrol, moveTo) ->
            if (!patrol.enabled || patrol.points.isEmpty()) return@exec
            if (moveTo?.enabled == true) return@exec
            val mob = mcEntity as? PathfinderMob ?: return@exec
            val entityId = mcEntity.uuid
            val currentIndex = AIRuntimeState.patrolIndices.getOrDefault(entityId, 0)
                .coerceIn(0, patrol.points.lastIndex)
            val point = patrol.points[currentIndex]
            val arrivalDistanceSqr = patrol.arrivalRadius * patrol.arrivalRadius

            if (mcEntity.distanceToSqr(point.position) <= arrivalDistanceSqr.toDouble()) {
                val waitTicks = AIRuntimeState.patrolWaits.getOrElse(entityId) { point.waitTicks.coerceAtLeast(0) }
                if (waitTicks > 0) {
                    AIRuntimeState.patrolWaits[entityId] = waitTicks - 1
                    mob.navigation.stop()
                    if (point.lookAtNextPoint) {
                        patrol.points.getOrNull((currentIndex + 1).coerceAtMost(patrol.points.lastIndex))?.let { nextPoint ->
                            mob.faceTowards(nextPoint.position)
                        }
                    }
                    return@exec
                }

                AIRuntimeState.patrolWaits.remove(entityId)
                val nextIndex = currentIndex + 1
                AIRuntimeState.patrolIndices[entityId] = when {
                    nextIndex <= patrol.points.lastIndex -> nextIndex
                    patrol.loop -> 0
                    else -> patrol.points.lastIndex
                }
                if (!patrol.loop && currentIndex == patrol.points.lastIndex) {
                    mob.navigation.stop()
                    return@exec
                }
            }

            val activeIndex = AIRuntimeState.patrolIndices.getOrDefault(entityId, currentIndex)
                .coerceIn(0, patrol.points.lastIndex)
            val activePoint = patrol.points[activeIndex]
            mob.moveTowards(activePoint.position, patrol.speed.toDouble(), patrol.arrivalRadius.toDouble())
        }

    system(query<MCEntity, LookAtTargetComponent>())
        .named("LookAtTargetSystem")
        .exec { (mcEntity, lookAt) ->
            if (!lookAt.enabled) return@exec
            val mob = mcEntity as? Mob ?: return@exec
            when (lookAt.targetMode) {
                LookTargetMode.ENTITY -> {
                    val target = lookAt.targetEntity.resolve(mcEntity.level()) ?: return@exec
                    mob.faceTowards(target, lookAt.yawSpeed)
                }

                LookTargetMode.POSITION -> {
                    mob.faceTowards(lookAt.targetPosition, lookAt.yawSpeed)
                }
            }
        }

    system(query<MCEntity, PickupLootComponent>())
        .named("PickupLootSystem")
        .exec { (mcEntity, pickup) ->
            if (!pickup.enabled) return@exec
            val mob = mcEntity as? Mob ?: return@exec
            val pathfinder = mcEntity as? PathfinderMob ?: return@exec
            val remainingCooldown = AIRuntimeState.lootScanCooldowns.getOrDefault(mcEntity.uuid, 0)
            if (remainingCooldown > 0) {
                AIRuntimeState.lootScanCooldowns[mcEntity.uuid] = remainingCooldown - 1
                return@exec
            }

            mob.setCanPickUpLoot(true)
            val radius = pickup.radius.toDouble()
            val item = mcEntity.level().getEntitiesOfClass(ItemEntity::class.java, mcEntity.boundingBox.inflate(radius))
                .asSequence()
                .filter { !it.isRemoved && !it.item.isEmpty && !it.hasPickUpDelay() }
                .minByOrNull { it.distanceToSqr(mcEntity) }

            if (item != null) {
                pathfinder.moveTowards(item, 1.0)
                mob.faceTowards(item)
            }

            AIRuntimeState.lootScanCooldowns[mcEntity.uuid] = pickup.scanIntervalTicks.coerceAtLeast(1)
        }

    observe<OnRemove>().involving<AttackTargetComponent>().exec(query<MCEntity>()) { (mcEntity) ->
        AIRuntimeState.attackCooldowns.remove(mcEntity.uuid)
    }
    observe<OnRemove>().involving<PatrolPathComponent>().exec(query<MCEntity>()) { (mcEntity) ->
        AIRuntimeState.patrolIndices.remove(mcEntity.uuid)
        AIRuntimeState.patrolWaits.remove(mcEntity.uuid)
    }
    observe<OnRemove>().involving<PickupLootComponent>().exec(query<MCEntity>()) { (mcEntity) ->
        AIRuntimeState.lootScanCooldowns.remove(mcEntity.uuid)
    }
}




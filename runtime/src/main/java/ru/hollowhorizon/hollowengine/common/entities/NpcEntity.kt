package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.binding.EntitySnapshotPacket
import ru.hollowhorizon.hollowengine.common.geary.components.HitboxComponent
import ru.hollowhorizon.hollowengine.common.geary.components.hitboxComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcMoveControl
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcPathNavigation
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.utils.FakePlayer
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl

class NpcEntity : PathfinderMob {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY, level)
    constructor(type: EntityType<NpcEntity>, world: Level) : super(type, world)

    init {
        moveControl = NpcMoveControl(this)
    }

    val goals get() = goalSelector

    val fakePlayer: ServerPlayer by lazy {
        val player = FakePlayer.create(level() as ServerLevel)
        player.setGameMode(GameType.CREATIVE)
        player
    }

    init {
        setCanPickUpLoot(true)
    }


    override fun createNavigation(pLevel: Level) = NpcPathNavigation(pLevel, this)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND && level().isClientSide && pPlayer.mainHandItem.item != ModItems.NPC_TOOL) {
            //NPCMenuGui(this).open()
            return InteractionResult.SUCCESS
        }

        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, false))
    }

    override fun isInvulnerable() = true
    override fun shouldDespawnInPeaceful() = false
    override fun canPickUpLoot() = true
    override fun wantsToPickUp(pStack: ItemStack) = false


    override fun doPush(pEntity: Entity) {
        if (hitboxMode != HitboxMode.EMPTY) super.doPush(pEntity)
    }

    override fun isPushable(): Boolean {
        return super.isPushable() && hitboxMode == HitboxMode.PULLING
    }

    override fun canBeCollidedWith(): Boolean {
        return hitboxMode == HitboxMode.BLOCKING && isAlive
    }

    override fun aiStep() {
        updateSwingTime()
        super.aiStep()
    }

    override fun removeWhenFarAway(dist: Double) = false
    override fun isPersistenceRequired() = true

    val pickupDistance get() = pickupReach

    var hitboxMode: HitboxMode
        get() = hitboxComponent?.mode ?: HitboxMode.PULLING
        set(value) {
            set(HitboxComponent(value))
            EntitySnapshotPacket(
                id,
                snapshotOf(this),
            ).sendTrackingEntityAndSelf(this)
        }

    var name: String
        get() = displayName?.string ?: ""
        set(value) {
            customName = value.literal
            isCustomNameVisible = value.isNotEmpty()
        }

    fun seat() {
        SeatEntity.seat(this, direction)
    }

    fun standup() {
        vehicle?.let { stopRiding() }
    }

    fun clearTarget() {
        target = null
    }

    fun setAttributes(attributes: Map<String, Float>) {
        attributes.forEach { (attributeName, value) ->
            BuiltInRegistries.ATTRIBUTE.getHolder(attributeName.rl).orElseThrow()
                ?.let { attribute ->
                    this.attributes.getInstance(attribute)?.let { instance ->
                        instance.baseValue = value.toDouble()
                    }
                }
        }
    }


    companion object {

        fun createAttributes(): AttributeSupplier.Builder {
            return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.LUCK, 0.0)
        }

    }
}

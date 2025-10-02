package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.lifecycle.attach
import ru.hollowhorizon.hollowengine.common.components.lifecycle.get
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcMoveControl
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcPathNavigation
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.registry.ModItems

@ComponentMeta("hollowengine:npcs/main")
class NpcComponent: Component<NpcEntity>() {
    var hitboxMode by property { HitboxMode.PULLING }
}

class NpcEntity : PathfinderMob {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY, level)
    constructor(type: EntityType<NpcEntity>, world: Level) : super(type, world)

    init {
        moveControl = NpcMoveControl(this)
        if(!level().isClientSide) (this as ComponentDispatcher).apply {
            attach("hollowengine:npcs/main".rl)
            attach("hollowengine:model_renderer".rl)
            attach("hollowengine:animator".rl)
        }
    }

    val goals get() = goalSelector

    val fakePlayer: ServerPlayer by lazy {
        //? if fabric {
        val player = net.fabricmc.fabric.api.entity.FakePlayer.get(level() as ServerLevel)
        //?} elif forge {
        /*val player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level() as ServerLevel)
        *///?}
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

    override fun dropAllDeathLoot(damageSource: DamageSource) {
        super.dropAllDeathLoot(damageSource)
    }

    override fun doPush(pEntity: Entity) {
        if (get<NpcComponent>()?.hitboxMode != HitboxMode.EMPTY) super.doPush(pEntity)
    }

    override fun isPushable(): Boolean {
        return super.isPushable() && get<NpcComponent>()?.hitboxMode == HitboxMode.PULLING
    }

    override fun canBeCollidedWith(): Boolean {
        return get<NpcComponent>()?.hitboxMode == HitboxMode.BLOCKING && this.isAlive
    }

    override fun aiStep() {
        updateSwingTime()
        super.aiStep()
    }

    override fun removeWhenFarAway(dist: Double) = false
    override fun isPersistenceRequired() = true

    val pickupDistance get() = pickupReach

    var hitboxMode: HitboxMode
        get() = get<NpcComponent>()?.hitboxMode ?: HitboxMode.PULLING
        set(value) {
            get<NpcComponent>()?.hitboxMode = value
        }

    var name: String
        get() = displayName.string
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
            BuiltInRegistries.ATTRIBUTE[attributeName.rl]?.let { attribute ->
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
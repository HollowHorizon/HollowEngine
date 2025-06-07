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
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.IAnimated
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcMoveControl
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcPathNavigation
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponent
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponentsCapability

class NpcEntity : PathfinderMob, IAnimated {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY, level)
    constructor(type: EntityType<NpcEntity>, world: Level) : super(type, world)

    init {
        moveControl = NpcMoveControl(this)
        this[AnimatedEntityCapability::class].model = "hollowengine:models/entity/player_model.gltf"
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

    val components: MutableList<NpcComponent>
        get() = this[NpcComponentsCapability::class].components
            .onEach { it.npc = this@NpcEntity }

    init {
        setCanPickUpLoot(true)
        createAttributes()
    }


    override fun defineSynchedData() {
        entityData.define(sizeX, 0.6f)
        entityData.define(sizeY, 1.8f)
        super.defineSynchedData()
    }

    override fun createNavigation(pLevel: Level) = NpcPathNavigation(pLevel, this)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND) {
            components.forEach { it.onInteract(pPlayer, pHand) }
        }

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

    public override fun pickUpItem(pItemEntity: ItemEntity) {
        components.firstOrNull { it.canPickup(pItemEntity) }?.let {
            val item = pItemEntity.item
            onItemPickup(pItemEntity)
            take(pItemEntity, item.count)
            pItemEntity.discard()
        }
    }

    override fun customServerAiStep() {
        components.forEach { it.tick() }

        val capability = this[NPCCapability::class]

        if (capability.currentTrade == -1) return

        if (capability.currentTrade >= capability.trades.size) {
            capability.currentTrade = -1
            return
        }

        val trade = capability.trades[capability.currentTrade]
        if (trade.matches(capability.tradeContainer)) {
            capability.tradeContainer.setItem(6, trade.output.copy())
        } else if (!capability.tradeContainer.getItem(6).isEmpty) {
            capability.tradeContainer.setItem(6, ItemStack.EMPTY)
        }

    }

    override fun dropEquipment() {
        super.dropEquipment()
        val tradeSlots = this[NPCCapability::class].tradeContainer

        (0 until tradeSlots.size).map { tradeSlots.getItem(it) }.forEach(::spawnAtLocation)
        tradeSlots.clearContent()
    }

    override fun dropAllDeathLoot(damageSource: DamageSource) {
        super.dropAllDeathLoot(damageSource)
        components.forEach { it.onDeath(damageSource) }
    }

    override fun doPush(pEntity: Entity) {
        if (this[NPCCapability::class].hitboxMode != HitboxMode.EMPTY) super.doPush(pEntity)
    }

    override fun isPushable(): Boolean {
        return super.isPushable() && this[NPCCapability::class].hitboxMode == HitboxMode.PULLING
    }

    override fun canBeCollidedWith(): Boolean {
        return this[NPCCapability::class].hitboxMode == HitboxMode.BLOCKING && this.isAlive
    }

    override fun aiStep() {
        updateSwingTime()
        super.aiStep()
    }

    override fun removeWhenFarAway(dist: Double) = false
    override fun isPersistenceRequired() = true

    override fun onSyncedDataUpdated(pKey: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(pKey)
        if (pKey == sizeX || pKey == sizeY) refreshDimensions()
    }

    override fun getDimensions(pose: Pose): EntityDimensions =
        EntityDimensions.fixed(entityData[sizeX], entityData[sizeY])

    fun setDimensions(xy: Pair<Float, Float>) {
        entityData.apply {
            set(sizeX, xy.first)
            set(sizeY, xy.second)
        }
    }

    override fun save(pCompound: CompoundTag): Boolean {
        super.save(pCompound)
        pCompound.putFloat("sizeX", entityData[sizeX])
        pCompound.putFloat("sizeY", entityData[sizeY])
        return true
    }

    override fun load(pCompound: CompoundTag) {
        super.load(pCompound)

        entityData[sizeX] = pCompound.getFloat("sizeX")
        entityData[sizeY] = pCompound.getFloat("sizeY")
        components.clear()
    }

    val pickupDistance get() = pickupReach

    var hitboxMode: HitboxMode
        get() = this[NPCCapability::class].hitboxMode
        set(value) {
            this[NPCCapability::class].hitboxMode = value
        }

    var icon: NpcIcon
        get() = this[NPCCapability::class].icon
        set(value) {
            this[NPCCapability::class].icon = value
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

    private fun getAttributeByName(name: String): Attribute? {
        return when (name.lowercase().replace("generic.", "")) {
            "max_health", "health" -> Attributes.MAX_HEALTH
            "movement_speed", "speed" -> Attributes.MOVEMENT_SPEED
            "armor" -> Attributes.ARMOR
            "armor_toughness" -> Attributes.ARMOR_TOUGHNESS
            "attack_damage", "damage" -> Attributes.ATTACK_DAMAGE
            "attack_speed" -> Attributes.ATTACK_SPEED
            "attack_knockback", "knockback" -> Attributes.ATTACK_KNOCKBACK
            "follow_range", "range" -> Attributes.FOLLOW_RANGE
            "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE
            "flying_speed", "fly_speed" -> Attributes.FLYING_SPEED
            "luck" -> Attributes.LUCK
            else -> null
        }
    }


    companion object {
        @JvmField
        val sizeX: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NpcEntity::class.java, EntityDataSerializers.FLOAT)

        @JvmField
        val sizeY: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NpcEntity::class.java, EntityDataSerializers.FLOAT)

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
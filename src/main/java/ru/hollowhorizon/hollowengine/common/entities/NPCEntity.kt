package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.FakePlayerFactory
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.capabilities.ICapabilitySyncer
import ru.hollowhorizon.hollowengine.client.render.effects.EffectsCapability
import ru.hollowhorizon.hollowengine.client.render.effects.ParticleEffect
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.MerchantNpc
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcTarget
import ru.hollowhorizon.hollowengine.common.npcs.goals.BlockBreakGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.LadderClimbGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.OpenDoorGoal
import ru.hollowhorizon.hollowengine.common.npcs.pathing.NPCPathNavigator
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IAnimated, ICapabilitySyncer {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY.get(), level)
    constructor(type: EntityType<NPCEntity>, world: Level) : super(type, world)

    val fakePlayer by lazy {
        FakePlayerFactory.getMinecraft(level as ServerLevel).apply {
            setGameMode(GameType.CREATIVE)
        }
    }
    var onInteract: (Player) -> Unit = {}
    var shouldGetItem: (ItemStack) -> Boolean = { false }
    val npcTarget = NpcTarget(level)
    val npcTrader = MerchantNpc()

    init {
        setCanPickUpLoot(true)
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(sizeX, 0.6f)
        entityData.define(sizeY, 1.8f)
    }

    override fun addAdditionalSaveData(pCompound: CompoundTag) {
        super.addAdditionalSaveData(pCompound)
        pCompound.put("npc_target", npcTarget.serializeNBT())
        pCompound.put("npc_trades", npcTrader.npcOffers.createTag())
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {
        super.readAdditionalSaveData(pCompound)
        npcTarget.deserializeNBT(pCompound["npc_target"] as? CompoundTag ?: return)
        npcTrader.npcOffers = MerchantOffers(pCompound.getCompound("npc_trades"))
    }

    override fun createNavigation(pLevel: Level) = NPCPathNavigator(this, pLevel)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND) {
            if (npcTrader.npcOffers.size > 0 && !pPlayer.level.isClientSide) {
                npcTrader.tradingPlayer = pPlayer
                npcTrader.openTradingScreen(pPlayer, name, 1)
            }

            onInteract(pPlayer)
        }

        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, false))
        goalSelector.addGoal(1, LadderClimbGoal(this))
        goalSelector.addGoal(1, BlockBreakGoal(this))
        goalSelector.addGoal(1, OpenDoorGoal(this))
    }

    fun addEffect(effect: ParticleEffect) {
        this[EffectsCapability::class].effects.add(effect)
    }

    override fun isInvulnerable() = true

    override fun shouldDespawnInPeaceful() = false

    override fun canPickUpLoot(): Boolean {
        return true
    }

    override fun wantsToPickUp(pStack: ItemStack): Boolean {
        return shouldGetItem(pStack)
    }

    override fun pickUpItem(pItemEntity: ItemEntity) {
        val item = pItemEntity.item
        onItemPickup(pItemEntity)
        this.take(pItemEntity, item.count)
        pItemEntity.discard()
    }

    override fun tick() {
        super.tick()
        npcTarget.tick(this)
    }

    override fun doPush(pEntity: Entity) {
        if(this[NPCCapability::class].hitboxMode != HitboxMode.EMPTY) super.doPush(pEntity)
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

    override fun onCapabilitySync(capability: Capability<*>) {
        if (capability.name.contains("AnimatedEntityCapability")) {
            HollowCore.LOGGER.info("Model: {}", this[AnimatedEntityCapability::class].model)
        }
    }

    override fun onSyncedDataUpdated(pKey: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(pKey)
        if (pKey == sizeX || pKey == sizeY) refreshDimensions()
    }

    override fun getDimensions(pPose: Pose): EntityDimensions {
        return EntityDimensions.fixed(entityData[sizeX], entityData[sizeY])
    }

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
    }

    companion object {
        @JvmField
        val sizeX: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)

        @JvmField
        val sizeY: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
    }
}

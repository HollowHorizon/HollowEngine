package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hollowengine.client.gui.npcs.trading.TradeMenuGui
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IAnimated, Merchant {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY.get(), level)
    constructor(type: EntityType<NPCEntity>, world: Level) : super(type, world)

    var onInteract: (Player) -> Unit = EMPTY_INTERACT
    var shouldGetItem: (ItemStack) -> Boolean = { false }
    private var tradePlayer: Player? = null
    var npcOffers = MerchantOffers()

    init {
        setCanPickUpLoot(true)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(sizeX, 0.6f)
        builder.define(sizeY, 1.8f)
        super.defineSynchedData(builder)
    }

    override fun setTradingPlayer(pTradingPlayer: Player?) {
        tradePlayer = pTradingPlayer
    }

    override fun getTradingPlayer() = tradePlayer

    override fun getOffers() = npcOffers

    override fun overrideOffers(pOffers: MerchantOffers) {}

    override fun notifyTrade(pOffer: MerchantOffer) {
        pOffer.increaseUses()

        ExperienceOrb.award(level() as? ServerLevel ?: return, position(), pOffer.xp)

    }

    override fun notifyTradeUpdated(pStack: ItemStack) {
    }

    override fun getVillagerXp() = 0

    override fun overrideXp(pXp: Int) {}

    override fun showProgressBar() = false

    override fun getNotifyTradeSound() = SoundEvents.VILLAGER_YES

    override fun isClientSide() = level().isClientSide

    override fun addAdditionalSaveData(pCompound: CompoundTag) {
        super.addAdditionalSaveData(pCompound)
        MerchantOffers.CODEC
            .encodeStart(NbtOps.INSTANCE, npcOffers)
            .resultOrPartial { HollowCore.LOGGER.warn("Error when serializing npc trades: {}", it) }
            .ifPresent {
                pCompound.put("npc_trades", it)
            }
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {
        super.readAdditionalSaveData(pCompound)
        MerchantOffers.CODEC
            .parse(NbtOps.INSTANCE, pCompound.get("npc_trades"))
            .resultOrPartial { HollowCore.LOGGER.warn("Error when reading npc trades: {}", it) }
            .ifPresent {
                npcOffers = it
            }
    }

    override fun createNavigation(pLevel: Level) = super.createNavigation(pLevel)
        .apply { nodeEvaluator.setCanOpenDoors(true); nodeEvaluator.setCanPassDoors(true) } //NPCPathNavigatorV2(this, pLevel)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND && level().isClientSide) {
            if (pPlayer.getItemInHand(pHand).isEmpty) {
                TradeMenuGui(this, false).open()
                return InteractionResult.SUCCESS
            }
        }

        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, false))
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

    override fun customServerAiStep() {
        val capability = this[NPCCapability::class]

        if (capability.currentTrade != -1) {
            if(capability.currentTrade >= capability.trades.size) {
                capability.currentTrade = -1
                return
            }
            val trade = capability.trades[capability.currentTrade]
            if (trade.matches(capability.tradeContainer)) capability.tradeContainer.setItem(6, trade.output.copy())
            else if (!capability.tradeContainer.getItem(6).isEmpty) {
                capability.tradeContainer.setItem(6, ItemStack.EMPTY)
            }
        }
    }

    override fun dropEquipment() {
        super.dropEquipment()
        val tradeSlots = this[NPCCapability::class].tradeContainer

        tradeSlots.items.forEach(::spawnAtLocation)
        tradeSlots.clearContent()
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

    override fun getDefaultDimensions(pPose: Pose): EntityDimensions {
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
        val EMPTY_INTERACT: (Player) -> Unit = {}

        @JvmField
        val sizeX: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)

        @JvmField
        val sizeY: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
    }
}
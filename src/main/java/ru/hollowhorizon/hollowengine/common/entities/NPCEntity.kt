/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.FakePlayerFactory
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.capabilities.ICapabilitySyncer
import ru.hollowhorizon.hollowengine.client.render.effects.EffectsCapability
import ru.hollowhorizon.hollowengine.client.render.effects.ParticleEffect
import ru.hollowhorizon.hollowengine.common.capabilities.StoriesCapability
import ru.hollowhorizon.hollowengine.common.npcs.FlyingNpcMoveControl
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcTarget
import ru.hollowhorizon.hollowengine.common.npcs.goals.BlockBreakGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.LadderClimbGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.OpenDoorGoal
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IAnimated, Merchant, ICapabilitySyncer {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY.get(), level)
    constructor(type: EntityType<NPCEntity>, world: Level) : super(type, world)

    val fakePlayer by lazy {
        FakePlayerFactory.getMinecraft(level as ServerLevel).apply {
            setGameMode(GameType.CREATIVE)
        }
    }
    var onInteract: (Player) -> Unit = EMPTY_INTERACT
    var shouldGetItem: (ItemStack) -> Boolean = { false }
    val npcTarget = NpcTarget(level)
    private var tradePlayer: Player? = null
    var npcOffers = MerchantOffers()

    init {
        setCanPickUpLoot(true)

        this[NPCCapability::class].script.init(this)
    }

    var flying: Boolean
        get() = this[NPCCapability::class].flying
        set(value) {
            this[NPCCapability::class].flying = value
            navigation = createNavigation(level)
        }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(sizeX, 0.6f)
        entityData.define(sizeY, 1.8f)
    }

    override fun setTradingPlayer(pTradingPlayer: Player?) {
        tradePlayer = pTradingPlayer
    }

    override fun getTradingPlayer() = tradePlayer

    override fun getOffers() = npcOffers

    override fun overrideOffers(pOffers: MerchantOffers) {}

    override fun notifyTrade(pOffer: MerchantOffer) {
        pOffer.increaseUses()
        if (level is ServerLevel) {
            ExperienceOrb.award(level as ServerLevel, position(), pOffer.xp)
        }
    }

    override fun notifyTradeUpdated(pStack: ItemStack) {
    }

    override fun getVillagerXp() = 0

    override fun overrideXp(pXp: Int) {}

    override fun showProgressBar() = false

    override fun getNotifyTradeSound() = SoundEvents.VILLAGER_YES

    override fun isClientSide() = level.isClientSide

    override fun addAdditionalSaveData(pCompound: CompoundTag) {
        super.addAdditionalSaveData(pCompound)
        pCompound.put("npc_target", npcTarget.serializeNBT())
        pCompound.put("npc_trades", npcOffers.createTag())
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {
        super.readAdditionalSaveData(pCompound)
        npcTarget.deserializeNBT(pCompound["npc_target"] as? CompoundTag ?: return)
        npcOffers = MerchantOffers(pCompound.getCompound("npc_trades"))
    }

    override fun createNavigation(pLevel: Level): PathNavigation {
        val navigator =
            if (flying) FlyingPathNavigation(this, pLevel)
            else GroundPathNavigation(this, pLevel)
        navigator.nodeEvaluator.setCanOpenDoors(true)
        navigator.nodeEvaluator.setCanPassDoors(true)

        this.moveControl = if (flying) FlyingNpcMoveControl(this)
        else MoveControl(this)

        return navigator
    }

    override fun travel(pTravelVector: Vec3) {
        if (flying) {
            if (this.isEffectiveAi || this.isControlledByLocalInstance) {
                if (this.isInWater) {
                    this.moveRelative(0.02f, pTravelVector)
                    this.move(MoverType.SELF, this.deltaMovement)
                    this.deltaMovement = deltaMovement.scale(0.800000011920929)
                } else if (this.isInLava) {
                    this.moveRelative(0.02f, pTravelVector)
                    this.move(MoverType.SELF, this.deltaMovement)
                    this.deltaMovement = deltaMovement.scale(0.5)
                } else {
                    val ground = BlockPos(this.x, this.y - 1.0, this.z)
                    var f = 0.91f
                    if (this.onGround) {
                        f = level.getBlockState(ground)
                            .getFriction(this.level, ground, this) * 0.91f
                    }

                    val f1 = 0.16277137f / (f * f * f)
                    f = 0.91f
                    if (this.onGround) {
                        f = level.getBlockState(ground)
                            .getFriction(this.level, ground, this) * 0.91f
                    }

                    this.moveRelative(if (this.onGround) 0.1f * f1 else 0.02f, pTravelVector)
                    this.move(MoverType.SELF, this.deltaMovement)
                    this.deltaMovement = deltaMovement.scale(f.toDouble())
                }
            }

            this.calculateEntityAnimation(this, false)
        } else {
            super.travel(pTravelVector)
        }
    }

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND) {
            if (npcOffers.size > 0 && !pPlayer.level.isClientSide) {
                tradingPlayer = pPlayer
                openTradingScreen(pPlayer, name, 1)
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

        if (!isClientSide && isAlive) {
            this[NPCCapability::class].script.update()
        }
    }

    override fun remove(pReason: RemovalReason) {
        super.remove(pReason)

        val level = level as? ServerLevel ?: return
        val entities = level.entities.all.filterIsInstance<NPCEntity>()
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

    override fun die(pDamageSource: DamageSource) {
        super.die(pDamageSource)
        level[StoriesCapability::class].activeNpcs.remove(uuid.toString())
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

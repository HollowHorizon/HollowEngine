package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraftforge.common.util.FakePlayerFactory
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.client.render.effects.EffectsCapability
import ru.hollowhorizon.hollowengine.client.render.effects.ParticleEffect
import ru.hollowhorizon.hollowengine.common.npcs.NpcTarget
import ru.hollowhorizon.hollowengine.common.npcs.goals.BlockBreakGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.LadderClimbGoal
import ru.hollowhorizon.hollowengine.common.npcs.goals.OpenDoorGoal
import ru.hollowhorizon.hollowengine.common.npcs.pathing.NPCPathNavigator
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IAnimated {
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

    init {
        setCanPickUpLoot(true)
    }

    override fun addAdditionalSaveData(pCompound: CompoundTag) {
        super.addAdditionalSaveData(pCompound)
        pCompound.put("npc_target", npcTarget.serializeNBT())
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {
        super.readAdditionalSaveData(pCompound)
        npcTarget.deserializeNBT(pCompound.get("npc_target") as? CompoundTag ?: return)
    }

    override fun createNavigation(pLevel: Level) = NPCPathNavigator(this, pLevel)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND) onInteract(pPlayer)
        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
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

    override fun aiStep() {
        updateSwingTime()
        super.aiStep()
    }

    override fun removeWhenFarAway(dist: Double) = false
    override fun isPersistenceRequired() = true

    companion object
}

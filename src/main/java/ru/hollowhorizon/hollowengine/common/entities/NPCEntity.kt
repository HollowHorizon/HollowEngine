package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IAnimated {
    constructor(level: Level) : super(ModEntities.NPC_ENTITY.get(), level)
    constructor(type: EntityType<NPCEntity>, world: Level) : super(type, world)

    var onInteract: (Player) -> Unit = {}
    var shouldGetItem: (ItemStack) -> Boolean = { false }

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if(pHand == InteractionHand.MAIN_HAND) onInteract(pPlayer)
        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this)) //Если NPC решит утонить будет не кайф...
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, false))
    }

    override fun isInvulnerable() = true

    override fun shouldDespawnInPeaceful() = false

    //не думаю, что NPC можно деспавниться...
    override fun checkDespawn() {}


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

    companion object
}
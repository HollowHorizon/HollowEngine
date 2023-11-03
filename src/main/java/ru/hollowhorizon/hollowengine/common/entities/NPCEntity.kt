package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

class NPCEntity : PathfinderMob, IHollowNPC, IAnimated {
    val interactionWaiter = Object()

    constructor(level: Level) : super(ModEntities.NPC_ENTITY.get(), level)

    constructor(type: EntityType<NPCEntity>, world: Level) : super(type, world)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        synchronized(interactionWaiter) { interactionWaiter.notifyAll() }
        return super.mobInteract(pPlayer, pHand)
    }

    override fun die(source: DamageSource) {
        super.die(source)
    }

    override fun registerGoals() {

        goalSelector.addGoal(0, FloatGoal(this)) //Если NPC решит утонить будет не кайф...
    }

    override fun isInvulnerable() = true

    override fun shouldDespawnInPeaceful() = false

    //не думаю, что NPC можно деспавниться...
    override fun checkDespawn() {}
    override val npcEntity = this


    override fun canPickUpLoot(): Boolean {
        return true
    }

    companion object
}
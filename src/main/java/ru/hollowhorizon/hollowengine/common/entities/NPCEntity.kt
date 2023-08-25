package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.gltf.IAnimated
import ru.hollowhorizon.hc.client.gltf.animations.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.gltf.animations.manager.IModelManager
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent

class NPCEntity : PathfinderMob, IHollowNPC, IAnimated {
    val interactionWaiter = Object()
    val goalQueue = ArrayList<HollowNPCTask>()
    val removeGoalQueue = ArrayList<HollowNPCTask>()

    constructor(level: Level, model: String) : super(ModEntities.NPC_ENTITY.get(), level)

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

    override fun tick() {
        super.tick()

        //Без этого при добавлении тасков может вылететь ConcurrentModificationException
        if (goalQueue.size > 0) {
            goalQueue.forEach { task -> goalSelector.addGoal(0, task) }
            goalQueue.clear()
        }
        if (removeGoalQueue.size > 0) {
            removeGoalQueue.forEach { pTask -> goalSelector.removeGoal(pTask) }
            removeGoalQueue.clear()
        }
    }


    override fun canPickUpLoot(): Boolean {
        return true
    }

    override val manager by lazy { IModelManager.create(this) }

    companion object {

    }
}
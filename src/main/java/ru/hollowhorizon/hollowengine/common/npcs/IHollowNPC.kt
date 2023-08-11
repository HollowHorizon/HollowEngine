package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.coroutines.runBlocking
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.client.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.capabilities.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.capabilities.getCapability
import ru.hollowhorizon.hc.common.capabilities.syncEntity
import ru.hollowhorizon.hollowengine.common.capabilities.NPCEntityCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask

interface IHollowNPC : ICharacter {
    val npcEntity: NPCEntity
    override val entityType: CompoundTag
        get() = npcEntity.serializeNBT()
    override val characterName: String
        get() = npcEntity.displayName.string

    fun makeTask(task: HollowNPCTask.() -> Unit) {
        val pendingTask = HollowNPCTask(this)

        npcEntity.goalQueue.add(pendingTask)
        //npcEntity.goalSelector.addGoal(0, pendingTask)

        pendingTask.task()
    }

    fun configure(config: AnimatedEntityCapability.() -> Unit) {
        val animCapability = npcEntity.getCapability(AnimatedEntityCapability::class)
        config(animCapability)
        animCapability.syncEntity(npcEntity)
    }

    infix fun play(animation: String) {
        npcEntity.getCapability(AnimatedEntityCapability::class).animationsToStart.add(animation)
        npcEntity.getCapability(AnimatedEntityCapability::class).syncEntity(npcEntity)
    }

    fun play(animation: String, mode: PlayType) {
        npcEntity.getCapability(AnimatedEntityCapability::class).animationsToStart.add(animation)
        npcEntity.getCapability(AnimatedEntityCapability::class).syncEntity(npcEntity)
    }

    infix fun stop(animation: String) {
        npcEntity.getCapability(AnimatedEntityCapability::class).animationsToStop.add(animation)
        npcEntity.getCapability(AnimatedEntityCapability::class).syncEntity(npcEntity)
    }

    fun waitInteract(icon: IconType) {
        this.icon = icon
        synchronized(npcEntity.interactionWaiter) { npcEntity.interactionWaiter.wait() }
        this.icon = IconType.NONE
    }

    fun asyncTask(task: HollowNPCTask.() -> Unit) {
        val pendingTask = HollowNPCTask(this)

        runBlocking { // Необходимо, чтобы игра не зависла, пока внутренние действия не будут выполнены
            pendingTask.task()
        }

        npcEntity.goalSelector.addGoal(0, pendingTask)
        pendingTask.async()
    }

    var icon: IconType
        get() = npcEntity.getEntityIcon()
        set(value) = npcEntity.setEntityIcon(value)
}

enum class IconType {
    NONE, DIALOGUE, WARNING, QUESTION
}

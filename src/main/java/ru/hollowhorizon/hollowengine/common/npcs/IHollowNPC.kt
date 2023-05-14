package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.coroutines.runBlocking
import net.minecraft.nbt.CompoundNBT
import ru.hollowhorizon.hc.common.capabilities.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.capabilities.getCapability
import ru.hollowhorizon.hc.common.capabilities.syncEntity
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask

interface IHollowNPC : ICharacter {
    val npcEntity: NPCEntity
    override val entityType: CompoundNBT
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
        config(npcEntity.getCapability<AnimatedEntityCapability>())
    }

    infix fun play(animation: String) {
        npcEntity.getCapability<AnimatedEntityCapability>().manager.addAnimation(animation, loop = false)
        npcEntity.getCapability<AnimatedEntityCapability>().syncEntity(npcEntity)
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

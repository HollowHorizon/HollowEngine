package ru.hollowhorizon.hollowengine.common.npcs.tasks.look

import net.minecraft.entity.Entity
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

class NPCLook(val task: HollowNPCTask) {
    val keyframes = arrayListOf<LookKeyframe>()
    val waiter = Object()
    var isActive = false

    fun at(blockX: Int, blockY: Int, blockZ: Int, lookConfig: LookConfig.() -> Unit = {}): NPCLook {
        if (isActive) return this
        keyframes.add(BlockPosLookKeyframe(LookConfig().apply(lookConfig), task, blockX, blockY, blockZ))
        return this
    }

    fun at(blockX: Int, blockZ: Int, lookConfig: LookConfig.() -> Unit = { lockY = true }): NPCLook {
        if (isActive) return this
        keyframes.add(
            BlockPosLookKeyframe(
                LookConfig().apply(lookConfig),
                task,
                blockX,
                task.npc.npcEntity.blockPosition().y,
                blockZ
            )
        )
        return this
    }

    fun at(entity: Entity, lookConfig: LookConfig.() -> Unit = {}): NPCLook {
        if (isActive) return this
        keyframes.add(EntityLookKeyframe(LookConfig().apply(lookConfig), task, entity))
        return this
    }

    fun at(entity: IHollowNPC, lookConfig: LookConfig.() -> Unit = {}): NPCLook {
        if (isActive) return this
        keyframes.add(EntityLookKeyframe(LookConfig().apply(lookConfig), task, entity.npcEntity))
        return this
    }

    fun at(team: StoryTeam, lookConfig: LookConfig.() -> Unit = {}): NPCLook {
        if (isActive) return this
        keyframes.add(TeamLookKeyframe(LookConfig().apply(lookConfig), task, team))
        return this
    }

    class LookConfig {
        var rotTime: Float = 0.5f
        var lockX: Boolean = false
        var lockY: Boolean = false
    }

    fun async() {
        isActive = true
        task.isActive = true
    }

    fun await() {
        isActive = true
        task.isActive = true
        synchronized(waiter) {
            waiter.wait()
        }
    }
}
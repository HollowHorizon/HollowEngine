package ru.hollowhorizon.hollowstory.common.npcs.tasks.look

import net.minecraft.entity.Entity
import ru.hollowhorizon.hollowstory.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowstory.story.StoryTeam

interface LookKeyframe {
    val config: NPCLook.LookConfig
    val task: HollowNPCTask

    fun tick()
    fun isFinished(): Boolean
}

class BlockPosLookKeyframe(
    override val config: NPCLook.LookConfig,
    override val task: HollowNPCTask,
    blockX: Int,
    blockY: Int,
    blockZ: Int
) : LookKeyframe {
    val x = blockX.toDouble() + 0.5
    val y = blockY.toDouble()
    val z = blockZ.toDouble() + 0.5

    val lookControl = task.npc.npcEntity.lookControl
    var currentRot = 0f

    override fun tick() {
        lookControl.setLookAt(x, y, z)

        if(currentRot < config.rotTime) currentRot += 0.05f

    }

    override fun isFinished() = currentRot >= config.rotTime
}

class EntityLookKeyframe(
    override val config: NPCLook.LookConfig,
    override val task: HollowNPCTask,
    val entity: Entity
) : LookKeyframe {
    val lookControl = task.npc.npcEntity.lookControl
    var currentRot = 0f

    override fun tick() {
        lookControl.setLookAt(entity, 360f, 360f)

        if(currentRot < config.rotTime) currentRot += 0.05f

    }

    override fun isFinished() = currentRot >= config.rotTime
}

class TeamLookKeyframe(
    override val config: NPCLook.LookConfig,
    override val task: HollowNPCTask,
    val team: StoryTeam
) : LookKeyframe {
    val lookControl = task.npc.npcEntity.lookControl
    var currentRot = 0f

    override fun tick() {
        lookControl.setLookAt(team.nearestTo(task.npc).mcPlayer!!, 360f, 360f)

        if(currentRot < config.rotTime) currentRot += 0.05f

    }

    override fun isFinished() = currentRot >= config.rotTime
}
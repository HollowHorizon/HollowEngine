package ru.hollowhorizon.hollowengine.common.npcs.tasks.look

import net.minecraft.command.arguments.EntityAnchorArgument
import net.minecraft.entity.Entity
import net.minecraft.util.math.vector.Vector3d
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam

interface LookKeyframe {
    val config: NPCLook.LookConfig
    val task: HollowNPCTask

    fun onFinish()

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

    override fun onFinish() {
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Type.EYES, Vector3d(x, y, z))
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

    override fun onFinish() {
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Type.EYES, entity.position())
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

    override fun onFinish() {
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Type.EYES, team.nearestTo(task.npc).mcPlayer!!.position())
    }

    override fun isFinished() = currentRot >= config.rotTime
}
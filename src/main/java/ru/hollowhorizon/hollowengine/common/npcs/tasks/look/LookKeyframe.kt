package ru.hollowhorizon.hollowengine.common.npcs.tasks.look

import com.mojang.math.Vector3d
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
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
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3(x, y, z))
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
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Anchor.EYES, entity.position())
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
        task.npc.npcEntity.lookAt(EntityAnchorArgument.Anchor.EYES, team.nearestTo(task.npc).mcPlayer!!.position())
    }

    override fun isFinished() = currentRot >= config.rotTime
}
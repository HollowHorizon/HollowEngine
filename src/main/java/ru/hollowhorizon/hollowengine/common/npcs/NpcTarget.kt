package ru.hollowhorizon.hollowengine.common.npcs

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NpcTarget(val level: Level) : INBTSerializable<CompoundTag> {
    var movingPos: Vec3? = null
    var movingEntity: Entity? = null
    var movingTeam: Team? = null

    var lookingPos: Vec3? = null
    var lookingEntity: Entity? = null
    var lookingTeam: Team? = null

    fun tick(entity: NPCEntity) {
        if (movingPos != null) {
            entity.navigation.moveTo(entity.navigation.createPath(BlockPos(movingPos!!), 0), 1.0)
        }
        if (lookingPos != null) entity.lookControl.setLookAt(lookingPos!!.x, lookingPos!!.y, lookingPos!!.z)

        if (this.movingEntity != null) entity.navigation.moveTo(this.movingEntity!!, 1.0)
        if (this.lookingEntity != null) entity.lookAt(
            EntityAnchorArgument.Anchor.EYES,
            this.lookingEntity!!.eyePosition
        )

        if (this.movingTeam != null) {
            val nearest = this.movingTeam!!.onlineMembers!!.minByOrNull { it.distanceToSqr(entity) } ?: return
            entity.navigation.moveTo(nearest, 1.0)
        }
        if (this.lookingTeam != null) {
            val nearest = this.lookingTeam!!.onlineMembers!!.minByOrNull { it.distanceToSqr(entity) } ?: return
            entity.lookAt(EntityAnchorArgument.Anchor.EYES, nearest.eyePosition)
        }
    }

    override fun serializeNBT() = CompoundTag().apply {
        if (movingPos != null) {
            putDouble("mpos_x", movingPos!!.x)
            putDouble("mpos_y", movingPos!!.y)
            putDouble("mpos_z", movingPos!!.z)
        }
        if (movingEntity != null) putUUID("mentity", movingEntity!!.uuid)
        if (movingTeam != null) putUUID("mteam", movingTeam!!.id)

        if (lookingPos != null) {
            putDouble("lpos_x", lookingPos!!.x)
            putDouble("lpos_y", lookingPos!!.y)
            putDouble("lpos_z", lookingPos!!.z)
        }
        if (lookingEntity != null) putUUID("lentity", lookingEntity!!.uuid)
        if (lookingTeam != null) putUUID("lteam", lookingTeam!!.id)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        if (nbt.contains("mpos_x")) {
            movingPos = Vec3(
                nbt.getDouble("mpos_x"),
                nbt.getDouble("mpos_y"),
                nbt.getDouble("mpos_z")
            )
        }
        if (nbt.contains("mentity")) {
            val level = level as? ServerLevel ?: return
            movingEntity = level.getEntity(nbt.getUUID("mentity"))
        }
        if (nbt.contains("mteam")) {
            val teamId = nbt.getUUID("mteam")
            FTBTeamsAPI.getManager().getTeamByID(teamId)?.let {
                movingTeam = it
            }
        }

        if (nbt.contains("lpos_x")) {
            lookingPos = Vec3(
                nbt.getDouble("lpos_x"),
                nbt.getDouble("lpos_y"),
                nbt.getDouble("lpos_z")
            )
        }
        if (nbt.contains("lentity")) {
            val level = level as? ServerLevel ?: return
            lookingEntity = level.getEntity(nbt.getUUID("lentity"))
        }
        if (nbt.contains("lteam")) {
            val teamId = nbt.getUUID("lteam")
            FTBTeamsAPI.getManager().getTeamByID(teamId)?.let {
                lookingTeam = it
            }
        }
    }

}
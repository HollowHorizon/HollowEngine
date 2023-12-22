package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import kotlin.math.abs

open class NpcMoveToBlockNode(npcConsumer: NPCProperty, var pos: () -> Vec3) : Node() {
    val npc by lazy { npcConsumer() }
    val block by lazy { pos() }

    override fun tick(): Boolean {
        val navigator = npc.navigation

        navigator.moveTo(block.x, block.y, block.z, 1.0)

        return npc.distanceToXZ(block) > 1 || abs(npc.y - block.y) > 3
    }

    override fun serializeNBT() = CompoundTag().apply {
        putDouble("pos_x", block.x)
        putDouble("pos_y", block.y)
        putDouble("pos_z", block.z)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = { Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z")) }
    }
}

class NpcMoveToEntityNode(npcConsumer: NPCProperty, var target: () -> Entity?) : Node() {
    val npc by lazy { npcConsumer() }

    override fun tick(): Boolean {
        val navigator = npc.navigation
        val entity = target()
        navigator.moveTo(entity ?: return true, 1.0)

        return npc.distanceToXZ(entity) > 1.5 || abs(npc.y - entity.y) > 3
    }

    override fun serializeNBT() = CompoundTag().apply {
        val entity = target() ?: return@apply
        putString("level", entity.level.dimension().location().toString())
        putUUID("target", entity.uuid)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val level =
            manager.server.getLevel(manager.server.levelKeys().find { it.location() == nbt.getString("level").rl }
                ?: return) ?: return
        val entity = level.getEntity(nbt.getUUID("target")) ?: return
        target = { entity }
    }
}

class NpcMoveToTeamNode(npcConsumer: NPCProperty, var target: () -> Team?) : Node() {
    val npc by lazy { npcConsumer() }

    override fun tick(): Boolean {
        val navigator = npc.navigation

        val entity = target()?.onlineMembers?.minByOrNull { it.distanceToSqr(npc) } ?: return true

        navigator.moveTo(entity, 1.0)

        return npc.distanceToXZ(entity) > 1.5 || abs(npc.y - entity.y) > 3
    }

    override fun serializeNBT() = CompoundTag().apply {
        val team = target() ?: return@apply
        putUUID("team", team.id)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val team = FTBTeamsAPI.getManager().getTeamByID(nbt.getUUID("team"))
        if (team == null) HollowCore.LOGGER.warn("Team ${nbt.getUUID("team")} not found!")
        target = { team }
    }
}

fun Entity.distanceToXZ(pos: Vec3) = (x - pos.x) * (x - pos.x) + (z - pos.z) * (z - pos.z)
fun Entity.distanceToXZ(npc: Entity) = distanceToXZ(npc.position())
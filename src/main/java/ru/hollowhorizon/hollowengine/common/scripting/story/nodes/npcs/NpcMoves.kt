package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.ftb.mods.ftbteams.api.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcMoveToBlockNode(npcConsumer: NPCProperty, var pos: () -> Vec3) : Node() {
    val npc by lazy { npcConsumer() }
    override fun tick(): Boolean {
        val navigator = npc.navigation

        val nextPos = pos()

        navigator.moveTo(nextPos.x, nextPos.y, nextPos.z, 1.0)

        return !(navigator.path != null && navigator.isDone && npc.isOnGround)
    }

    override fun serializeNBT() = CompoundTag().apply {
        val nextPos = pos()
        putDouble("pos_x", nextPos.x)
        putDouble("pos_y", nextPos.y)
        putDouble("pos_z", nextPos.z)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = { Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z")) }
    }
}

class NpcMoveToEntityNode(npcConsumer: NPCProperty, var target: () -> Entity?) : Node() {
    val npc by lazy { npcConsumer() }

    override fun tick(): Boolean {
        val navigator = npc.navigation

        navigator.moveTo(target() ?: return true, 1.0)

        return !(navigator.path != null && navigator.isDone && npc.isOnGround)
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

        val npc = target()?.onlineMembers?.minByOrNull { it.distanceToSqr(npc) } ?: return true

        navigator.moveTo(npc, 1.0)

        return !(navigator.path != null && navigator.isDone && npc.isOnGround)
    }

    override fun serializeNBT() = CompoundTag().apply {
        val team = target() ?: return@apply
        putUUID("team", team.id)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val team = FTBTeamsAPI.api().manager.getTeamByID(nbt.getUUID("team"))
        if (team == null) HollowCore.LOGGER.warn("Team ${nbt.getUUID("team")} not found!")
        target = { team.get() }
    }
}
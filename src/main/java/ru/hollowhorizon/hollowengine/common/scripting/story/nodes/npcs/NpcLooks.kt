package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcLookToBlockNode(npcConsumer: NPCProperty, var pos: () -> Vec3, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        val newPos = pos()

        look.setLookAt(newPos.x, newPos.y, newPos.z, speed.x, speed.y)

        return ticks-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        val newPos = pos()
        putDouble("pos_x", newPos.x)
        putDouble("pos_y", newPos.y)
        putDouble("pos_z", newPos.z)
        putFloat("speed_x", speed.x)
        putFloat("speed_y", speed.y)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = { Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z")) }
        speed = Vec2(nbt.getFloat("speed_x"), nbt.getFloat("speed_y"))
    }
}

class NpcLookToEntityNode(npcConsumer: NPCProperty, var target: () -> Entity?, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        look.setLookAt(target() ?: return true, speed.x, speed.y)

        return ticks-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        val entity = target() ?: return@apply

        putString("level", entity.level.dimension().location().toString())
        putUUID("target", entity.uuid)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val level = manager.server.getLevel(manager.server.levelKeys().find { it.location() == nbt.getString("level").rl } ?: return) ?: return
        val entity = level.getEntity(nbt.getUUID("target")) ?: return
        target = { entity }
    }
}

class NpcLookToTeamNode(npcConsumer: NPCProperty, var target: () -> Team?, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        val team = target()?.onlineMembers?.minByOrNull { it.distanceToSqr(npc) } ?: return true

        look.setLookAt(team, speed.x, speed.y)

        return ticks-- > 0
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
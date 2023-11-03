package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcMoveToBlockNode(npcConsumer: NPCProperty, var pos: Vec3) : Node() {
    val npc by lazy { npcConsumer() }
    override fun tick(): Boolean {
        val navigator = npc.navigation

        navigator.moveTo(pos.x, pos.y, pos.z, 1.0)

        return !(navigator.path != null && navigator.isDone && npc.isOnGround)
    }

    override fun serializeNBT() = CompoundTag().apply {
        putDouble("pos_x", pos.x)
        putDouble("pos_y", pos.y)
        putDouble("pos_z", pos.z)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z"))
    }
}

class NpcMoveToEntityNode(npcConsumer: NPCProperty, var target: () -> Entity) : Node() {
    val npc by lazy { npcConsumer() }
    override fun tick(): Boolean {
        val navigator = npc.navigation

        navigator.moveTo(target(), 1.0)

        return !(navigator.path != null && navigator.isDone && npc.isOnGround)
    }

    override fun serializeNBT() = CompoundTag().apply {
        putString("level", target().level.dimension().location().toString())
        putUUID("target", target().uuid)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val level = manager.server.getLevel(manager.server.levelKeys().find { it.location() == nbt.getString("level").rl } ?: return) ?: return
        val entity = level.getEntity(nbt.getUUID("target")) ?: return
        target = { entity }
    }
}
package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.mixins.LookControlInvoker

class NpcLookToBlockNode(npcConsumer: NPCProperty, var pos: Vec3, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    override fun tick(): Boolean {
        val look = npc.lookControl

        look.setLookAt(pos.x, pos.y, pos.z, speed.x, speed.y)

        return look.isLookingAtTarget
    }

    override fun serializeNBT() = CompoundTag().apply {
        putDouble("pos_x", pos.x)
        putDouble("pos_y", pos.y)
        putDouble("pos_z", pos.z)
        putFloat("speed_x", speed.x)
        putFloat("speed_y", speed.y)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z"))
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
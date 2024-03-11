package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.screen.overlays.DrawMousePacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcInteractNode(npcConsumer: NPCProperty) : Node() {
    val npc by lazy { npcConsumer() }
    var hasInteracted = false
    var isStarted = false

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            DrawMousePacket(enable = true, onlyOnNpc = true).send(*manager.team.onlineMembers.toTypedArray())
            npc.onInteract = { player ->
                hasInteracted = true
            }
        }
        if (hasInteracted) {
            DrawMousePacket(enable = false, onlyOnNpc = false).send(*manager.team.onlineMembers.toTypedArray())
            npc.onInteract = NPCEntity.EMPTY_INTERACT
        }
        return !hasInteracted
    }

    override fun serializeNBT() = CompoundTag().apply {
        putBoolean("hasInteracted", hasInteracted)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        hasInteracted = nbt.getBoolean("hasInteracted")
        isStarted = false
    }
}
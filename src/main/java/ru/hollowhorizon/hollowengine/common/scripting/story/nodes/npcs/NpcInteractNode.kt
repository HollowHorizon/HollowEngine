package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcInteractNode(npcConsumer: NPCProperty) : Node() {
    val npc by lazy { npcConsumer() }
    var hasInteracted = false
    var isStarted = false

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            npc.onInteract = { player ->
                hasInteracted = true
            }
        }
        if (hasInteracted) {
            npc.onInteract = {}
        }
        return !hasInteracted
    }

    override fun serializeNBT() = CompoundTag().apply {
        putBoolean("hasInteracted", hasInteracted)
        putBoolean("isStarted", isStarted)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        hasInteracted = nbt.getBoolean("hasInteracted")
        isStarted = nbt.getBoolean("isStarted")
    }
}
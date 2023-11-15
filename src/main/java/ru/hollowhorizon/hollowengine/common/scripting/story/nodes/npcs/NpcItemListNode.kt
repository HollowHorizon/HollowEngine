package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class NpcItemListNode(val items: MutableList<ItemStack>, npcConsumer: NPCProperty) : Node() {
    val npc by lazy { npcConsumer() }
    var isStarted = false

    override fun tick(): Boolean {
        if(!isStarted) {
            isStarted = true
            npc.shouldGetItem = { entityItem ->
                val item = items.find { it.item == entityItem.item }

                if(item != null) {
                    val remaining = item.count
                    item.shrink(entityItem.count)
                    if(item.isEmpty) {
                        items.remove(item)
                        entityItem.shrink(remaining)
                    }
                }
                items.any { entityItem.item == it.item }
            }
            npc.onInteract = { player ->
                player.sendMessage("Тебе осталось принести: ".mcText, player.uuid)
                items.forEach {
                    player.sendMessage(TextComponent("- ").append(it.displayName).append(" x${it.count}"), player.uuid)
                }
            }
        }
        val hasItems = items.isNotEmpty()
        if(!hasItems) {
            npc.shouldGetItem = {false}
            npc.onInteract = {}
        }
        return hasItems
    }

    override fun serializeNBT() = CompoundTag().apply {
        put("items", ListTag().apply {
            addAll(items.map { it.save(CompoundTag()) })
        })
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        items.clear()
        nbt.getList("items", 10).forEach {
            items.add(ItemStack.of(it as CompoundTag))
        }
    }
}
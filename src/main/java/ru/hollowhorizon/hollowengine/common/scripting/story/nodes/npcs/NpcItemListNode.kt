package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class GiveItemList {
    val items = mutableListOf<ItemStack>()
    var text = "hollowengine.npc_need"

    operator fun ItemStack.unaryPlus() {
        items.add(this)
    }
}

class NpcItemListNode(itemList: GiveItemList.() -> Unit, npcConsumer: NPCProperty) : Node() {
    val npc by lazy { npcConsumer() }
    val itemList by lazy { GiveItemList().apply(itemList) }
    var isStarted = false

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            npc.shouldGetItem = { entityItem ->
                val item = itemList.items.find { it.item == entityItem.item }

                if (item != null) {
                    val remaining = item.count
                    item.shrink(entityItem.count)
                    if (item.isEmpty) {
                        itemList.items.remove(item)
                        entityItem.shrink(remaining)
                    }
                }
                itemList.items.any { entityItem.item == it.item }
            }
            npc.onInteract = { player ->
                player.sendMessage(itemList.text.mcTranslate, player.uuid)
                itemList.items.forEach {
                    player.sendMessage(TextComponent("- ").append(it.displayName).append(" x${it.count}"), player.uuid)
                }
            }
        }
        val hasItems = itemList.items.isNotEmpty()
        if (!hasItems) {
            npc.shouldGetItem = { false }
            npc.onInteract = {}
        }
        return hasItems
    }

    override fun serializeNBT() = CompoundTag().apply {
        put("items", ListTag().apply {
            addAll(itemList.items.map { it.save(CompoundTag()) })
        })
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        itemList.items.clear()
        nbt.getList("items", 10).forEach {
            itemList.items.add(ItemStack.of(it as CompoundTag))
        }
    }
}
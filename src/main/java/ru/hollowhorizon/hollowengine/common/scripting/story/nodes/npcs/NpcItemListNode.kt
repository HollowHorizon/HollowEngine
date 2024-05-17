/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hollowengine.client.screen.overlays.DrawMousePacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.util.Safe

open class GiveItemList {
    val items = mutableListOf<ItemStack>()
    var text = "hollowengine.npc_need"

    operator fun ItemStack.unaryPlus() {
        items.add(this)
    }
}

fun IContextBuilder.collectItems(block: GiveItemList.() -> Unit) = +ItemListNode(block)


class ItemListNode(itemList: GiveItemList.() -> Unit) : Node() {
    private val itemList by lazy { GiveItemList().apply(itemList) }

    override fun tick(): Boolean {
        var hasItems = false

        manager.server.playerList.players.forEach { member ->
            hasItems = hasItems ||
                    itemList.items.all { item ->
                        member.inventory.items.any { it.isItemStackEqual(item) }
                    }
        }

        return !hasItems
    }

    override fun serializeNBT() = CompoundTag()
    override fun deserializeNBT(nbt: CompoundTag) {}
}

private fun ItemStack.isItemStackEqual(it: ItemStack): Boolean {
    if (this.item != it.item) return false
    if (this.count != it.count) return false
    return true
}

class NpcItemListNode(itemList: GiveItemList.() -> Unit, val npc: Safe<NPCEntity>) : Node() {
    val itemList by lazy { GiveItemList().apply(itemList) }
    var isStarted = false

    override fun tick(): Boolean {
        if(!npc.isLoaded) return true
        val npc = npc()
        if (!isStarted || npc.onInteract == NPCEntity.EMPTY_INTERACT) {
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
            DrawMousePacket(enable = true).send(*manager.server.playerList.players.toTypedArray())
            npc[NPCCapability::class].mouseButton = MouseButton.RIGHT
            npc.onInteract = { player ->
                player.sendSystemMessage(itemList.text.mcTranslate)
                itemList.items.forEach {
                    player.sendSystemMessage(Component.literal("- ").append(it.displayName).append(" x${it.count}"))
                }
            }
        }
        val hasItems = itemList.items.isNotEmpty()
        if (!hasItems) {
            DrawMousePacket(enable = false).send(*manager.server.playerList.players.toTypedArray())
            npc.shouldGetItem = { false }
            npc[NPCCapability::class].mouseButton = MouseButton.NONE
            npc.onInteract = NPCEntity.EMPTY_INTERACT
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
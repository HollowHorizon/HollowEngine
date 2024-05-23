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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.npcs

import imgui.ImGui
import imgui.extension.nodeditor.NodeEditor
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centerWindow
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.item
import ru.hollowhorizon.hc.client.imgui.item
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph

class NpcGiveItemNode: NPCOperationNode() {
    var item: ItemStack = ItemStack.EMPTY
    private val isOpen = ImBoolean()

    override fun tick(graph: ScriptGraph) {
        val p = npc.position()
        val entityStack = ItemEntity(npc.level, p.x, p.y + npc.eyeHeight, p.z, item)
        entityStack.setDefaultPickUpDelay()
        val f8 = Mth.sin(npc.xRot * Mth.PI / 180f)
        val f3 = Mth.sin(npc.yHeadRot * Mth.PI / 180f)
        val f4 = Mth.cos(npc.yHeadRot * Mth.PI / 180f)
        entityStack.setDeltaMovement(
            -f3 * 0.3, -f8 * 0.3 + 0.1, f4 * 0.3
        )
        npc.level.addFreshEntity(entityStack)

        complete(graph)
    }

    override fun draw(graph: ScriptGraph) {
        item(item, 64f, 64f, true)
        if (ImGui.isItemClicked()) {
            isOpen.set(true)
        }
    }

    override fun drawPost(graph: ScriptGraph) {
        val player = Minecraft.getInstance().player ?: return
        if(isOpen.get()) ImGui.openPopup("Выбор предмета##item_picker")
        if (ImGui.beginPopupModal("Выбор предмета##item_picker", isOpen, ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove)) {
            //centerWindow()
            val inventory = player.inventory

            for ((i, invItem) in inventory.items.subList(9, 36).withIndex()) {
                item(invItem, 64f, 64f, true)
                if (ImGui.isItemClicked()) {
                    item = invItem.copy()
                    isOpen.set(false)
                }
                if ((i+1) % 9 != 0) ImGui.sameLine()
            }
            ImGui.separator()
            for ((i, invItem) in inventory.items.subList(0, 9).withIndex()) {
                item(invItem, 64f, 64f, true)
                if (ImGui.isItemClicked()) {
                    item = invItem.copy()
                    isOpen.set(false)
                }
                if ((i+1) % 9 != 0) ImGui.sameLine()
            }

            ImGui.endPopup()
        }
    }

    override fun serialize(tag: CompoundTag) {
        super.serialize(tag)
        tag.put("item", item.serializeNBT())
    }

    override fun deserialize(tag: CompoundTag) {
        super.deserialize(tag)
        item = ItemStack.of(tag.getCompound("item"))
    }
}

class NpcRequestItemNode: NPCOperationNode() {
    var item: ItemStack = ItemStack.EMPTY

    override fun tick(graph: ScriptGraph) {
        npc.shouldGetItem = { item.item == it.item }

        complete(graph)
    }

    override fun draw(graph: ScriptGraph) {
        val player = Minecraft.getInstance().player ?: return

        item(item, 64f, 64f, true)
        if (ImGui.isItemClicked()) ImGui.openPopup("Выбор предмета##item_picker")

        NodeEditor.suspend()
        if (ImGui.beginPopupModal("Выбор предмета##item_picker", ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove)) {
            centerWindow()
            val inventory = player.inventory

            for ((i, invItem) in inventory.items.subList(9, 36).withIndex()) {
                item(invItem, 64f, 64f, true)
                if (ImGui.isItemClicked()) {
                    item = invItem.copy()
                    ImGui.closeCurrentPopup()
                }
                if ((i+1) % 9 != 0) ImGui.sameLine()
            }
            ImGui.separator()
            for ((i, invItem) in inventory.items.subList(0, 9).withIndex()) {
                item(invItem, 64f, 64f, true)
                if (ImGui.isItemClicked()) {
                    item = invItem.copy()
                    ImGui.closeCurrentPopup()
                }
                if ((i+1) % 9 != 0) ImGui.sameLine()
            }

            ImGui.endPopup()
        }
        NodeEditor.resume()
    }

    override fun serialize(tag: CompoundTag) {
        super.serialize(tag)
        tag.put("item", item.serializeNBT())
    }

    override fun deserialize(tag: CompoundTag) {
        super.deserialize(tag)
        item = ItemStack.of(tag.getCompound("item"))
    }
}

/*
class NpcRequestItemsNode: NPCOperationNode() {
    val text by inPin<String, StringPin>()
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

    override fun serialize(tag: CompoundTag) {
        tag.put("items", ListTag().apply {
            addAll(itemList.items.map { it.save(CompoundTag()) })
        })
    }

    override fun deserialize(tag: CompoundTag) {
        itemList.items.clear()
        tag.getList("items", 10).forEach {
            itemList.items.add(ItemStack.of(it as CompoundTag))
        }
    }
}*/
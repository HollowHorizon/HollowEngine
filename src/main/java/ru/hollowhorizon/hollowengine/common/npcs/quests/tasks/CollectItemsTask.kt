package ru.hollowhorizon.hollowengine.common.npcs.quests.tasks

import imgui.ImGui
import imgui.flag.ImGuiMouseButton
import imgui.type.ImInt
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.addons.ItemProperties
import ru.hollowhorizon.hc.client.utils.nbt.ForItemStack
import ru.hollowhorizon.hollowengine.common.npcs.nodes.itemPicker
import ru.hollowhorizon.hollowengine.common.registry.QTask

@Serializable
@QTask("Принести предметы")
@Polymorphic(QuestTask::class)
class CollectItemsTask : AbstractQuestTask() {
    val items: MutableList<@Serializable(ForItemStack::class) ItemStack> = arrayListOf()
    var consumeItems = false

    override fun check(player: Player): Boolean {
        TODO("Not yet implemented")
    }

    override fun complete(player: Player): Boolean {
        TODO("Not yet implemented")
    }

    override fun drawEditor() {
        super.drawEditor()

        val player = Minecraft.getInstance().player ?: return

        Graphics.textShadow("Список предметов:")

        items.forEachIndexed { i, item ->
            Graphics.item(item, 80f, 80f, i.toString(), true, ItemProperties())
            if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                ImGui.openPopup("item_editor")
                ImGui.getStateStorage().setInt(ImGui.getID("item_index"), i)
            }
            if (((i + 1) % 9 != 0) || (i == items.size - 1)) ImGui.sameLine()
        }

        ImGui.newLine()

        drawItemMenu()

        if (ImGui.button("Добавить предмет")) {
            ImGui.openPopup("item_picker")
        }

        if (ImGui.isPopupOpen("item_picker") && ImGui.beginPopup("item_picker")) {
            player.inventory.itemPicker {
                items.add(it)
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun drawItemMenu() {
        val player = Minecraft.getInstance().player ?: return
        if (ImGui.isPopupOpen("item_editor")) {
            val slot = ImGui.getStateStorage().getInt(ImGui.getID("item_index"))
            val stack = items[slot]
            if (ImGui.beginPopup("item_editor")) {
                if(ImGui.beginMenu("Изменить количество")) {
                    val count = ImInt(stack.count)
                    Graphics.textShadow("Количество:"); ImGui.sameLine()
                    ImGui.pushItemWidth(120f)
                    ImGui.inputInt("##cout", count)
                    ImGui.popItemWidth()
                    stack.count = count.get().coerceIn(1, stack.maxStackSize)

                    ImGui.endMenu()
                }
                if(ImGui.menuItem("Удалить")) {
                    items.removeAt(slot)
                }

                ImGui.endPopup()
            }
        }
    }
}
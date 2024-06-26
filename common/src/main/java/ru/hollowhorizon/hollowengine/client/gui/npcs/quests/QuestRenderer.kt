package ru.hollowhorizon.hollowengine.client.gui.npcs.quests

import imgui.ImGui.*
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import ru.hollowhorizon.hc.client.imgui.BufferType
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.addons.ItemProperties
import ru.hollowhorizon.hc.client.imgui.currentBufferType
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestGraph
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestNode

object QuestRenderer {
    private val descriptionBuffer = ImString(500)
    var tooltip = {}

    fun drawPreview(questNode: QuestNode): Boolean {
        val pos = getCursorPos()
        val screenPos = getCursorScreenPos()
        imgui.internal.ImGui.getWindowDrawList().addImage(
            "hollowengine:textures/gui/quests/quest_icon.png".rl.toTexture().id,
            screenPos.x, screenPos.y,
            screenPos.x + 120f, screenPos.y + 120f,
            0f,
            0f,
            1f,
            1f,
            colorConvertFloat4ToU32(
                questNode.color[0],
                questNode.color[1],
                questNode.color[2],
                questNode.color[3]
            )
        )

        val old = currentBufferType
        currentBufferType = BufferType.BACKGROUND
        setCursorPos(pos.x + 20f, pos.y + 20f)
        ImGuiMethods.item(questNode.icon, 80f, 80f, properties = ItemProperties().apply {
            disableResize = true
            tooltip = false
        }, isNodeEditor = true)
        currentBufferType = old

        setCursorPos(pos.x, pos.y)
        dummy(120f, 120f)
        val clicked =
            imgui.internal.ImGui.isItemHovered() && imgui.internal.ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)

        if (isItemHovered()) {
            tooltip = {
                ImGuiMethods.tooltip {
                    textShadow(questNode.title)
                    separator()
                    pushColorStyle(ImGuiCol.Text, colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f)) {
                        textShadow(questNode.subtitle)
                    }
                }
            }
        }
        return clicked
    }

    fun drawEditor(graph: QuestGraph, node: QuestNode) {
        if (beginTabBar("##tabs")) {
            if (beginTabItem("Основное")) {
                drawGeneral(graph, node)
                endTabItem()
            }
            if (beginTabItem("Задания")) {
                drawTasks()
                endTabItem()
            }
            if (beginTabItem("Награды")) {
                drawRewards()
                endTabItem()
            }
            endTabBar()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun drawGeneral(graph: QuestGraph, node: QuestNode) {
        ImGuiMethods.textShadow("Индекс: ${graph.nodes.indexOf(node).toHexString(HexFormat.UpperCase)}")
        separator()

        pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
        pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)

        ImGuiMethods.textShadow("Описание: ")
        descriptionBuffer.set(node.description)
        inputTextMultiline("##description", descriptionBuffer)
        node.description = descriptionBuffer.get()

        separator()

        ImGuiMethods.textShadow("Сообщение при выполнении: ")
        descriptionBuffer.set(node.completeDescription)
        inputTextMultiline("##complete_desc", descriptionBuffer)
        node.completeDescription = descriptionBuffer.get()

        popStyleVar(2)

    }

    private fun drawTasks() {
        beginChild("##reward_list", getContentRegionMaxX(), getContentRegionAvailY() - 50f)

        repeat(15) {
            ImGuiMethods.textShadow("Задание: Принести предмет")
            ImGuiMethods.textShadow("Предмет: "); sameLine()
            ImGuiMethods.item(ItemStack(Items.DIAMOND), 80f, 80f)
            separator()
        }

        endChild()

        if (button("Добавить задание", getContentRegionMaxX(), 45f)) {
            openPopup("new_task")
        }
        taskPopup()
    }

    private fun taskPopup() {
        if (isPopupOpen("new_task") && beginPopup("new_task")) {
            ImGuiMethods.item(ItemStack(Items.DIAMOND), 40f, 40f); sameLine()
            if (selectable("Принести предметы")) {

            }

            ImGuiMethods.item(ItemStack(Items.IRON_SWORD), 40f, 40f); sameLine()
            if (selectable("Убить моба")) {

            }
            endPopup()
        }
    }

    private fun drawRewards() {
        beginChild("##reward_list", getContentRegionMaxX(), getContentRegionAvailY() - 50f)

        repeat(15) {
            ImGuiMethods.textShadow("Награда: Предмет")
            ImGuiMethods.textShadow("Предмет: "); sameLine()
            ImGuiMethods.item(ItemStack(Items.DIAMOND), 80f, 80f)
            separator()
        }

        endChild()

        if (button("Добавить награду", getContentRegionMaxX(), 45f)) {
            openPopup("new_reward")
        }

        if (isPopupOpen("new_reward") && beginPopup("new_reward")) {
            ImGuiMethods.item(ItemStack(Items.DIAMOND), 40f, 40f); sameLine()
            if (selectable("Предмет")) {

            }

            ImGuiMethods.item(ItemStack(Items.COMMAND_BLOCK), 40f, 40f); sameLine()
            if (selectable("Команда")) {

            }
            endPopup()
        }
    }
}
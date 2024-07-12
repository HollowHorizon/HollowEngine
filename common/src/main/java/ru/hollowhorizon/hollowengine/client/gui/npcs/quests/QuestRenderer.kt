package ru.hollowhorizon.hollowengine.client.gui.npcs.quests

import imgui.ImGui.*
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.type.ImString
import ru.hollowhorizon.hc.client.imgui.BufferType
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.addons.ItemProperties
import ru.hollowhorizon.hc.client.imgui.currentBufferType
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestNode

object QuestRenderer {
    private val descriptionBuffer = ImString(500)
    var tooltip = {}

    fun drawPreview(questNode: QuestNode, editMode: Boolean): Boolean {
        val pos = getCursorPos()
        val screenPos = getCursorScreenPos()
        imgui.internal.ImGui.getWindowDrawList().addImage(
            "hollowengine:textures/gui/quests/quest_icon.png".rl.toTexture().id,
            screenPos.x, screenPos.y,
            screenPos.x + 125f, screenPos.y + 110f,
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
        setCursorPos(pos.x + 25f, pos.y + 15f)
        ImGuiMethods.item(questNode.icon, 75f, 80f, properties = ItemProperties().apply {
            disableResize = true
            tooltip = false
        }, isNodeEditor = true)
        currentBufferType = old

        setCursorPos(pos.x, pos.y)
        dummy(125f, 110f)
        val clicked = isItemHovered() && (if (editMode) isMouseDoubleClicked(ImGuiMouseButton.Left) else isMouseClicked(
            ImGuiMouseButton.Left
        ))

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
}
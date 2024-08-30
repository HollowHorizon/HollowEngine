package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.npcs.quests.AcceptedQuestsCapability
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestNode
import kotlin.math.min

class QuestsListGui : ImGuiScreen() {
    private var scale = 1f
    val quests = Minecraft.getInstance().player[AcceptedQuestsCapability::class]
    var currentQuest: QuestNode? = null
    var page = 0

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width * 0.75f / 242f, window.height * 0.75f / 170f)
    }

    override fun Graphics.draw() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, scale * 2, scale * 2)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 3f * scale)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0.82f, 0.41f, 0f, ImGui.getStyle().alpha)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 1f, 0.5f, 0f, ImGui.getStyle().alpha)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0.9f, 0.4f, 0f, ImGui.getStyle().alpha)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0f, 0f, 0f, 0f)
        centredWindow {
            val fontSize = when (scale) {
                in 0f..1.5f -> 15
                in 1.5f..2.2f -> 19
                in 2.2f..2.5f -> 30
                in 2.5f..3f -> 50
                else -> 40
            }

            withFontSize(fontSize) {
                val pos = ImGui.getCursorPos()
                ImGui.image(
                    "hollowengine:textures/gui/quests/quests_menu.png".rl.toTexture().id,
                    242f * scale,
                    170f * scale
                )

                ImGui.setCursorPos(pos.x + 35 * scale, pos.y + 4 * scale)
                withFontSize((100 * scale / 6.511f).toInt()) {
                    textShadow("Квесты")
                    ImGui.sameLine()
                    ImGui.setCursorPosX(pos.x + 155 * scale)
                    val text = when (page) {
                        0 -> "Описание"
                        1 -> "Задание"
                        2 -> "Награды"
                        else -> "???"
                    }
                    textShadow(text)
                }

                ImGui.setCursorPos(pos.x + 9f * scale, pos.y + 30 * scale)

                val startPos = ImGui.getWindowPos()
                val isHovered = ImGui.isMouseHoveringRect(
                    startPos.x, startPos.y,
                    startPos.x + 242f * scale,
                    startPos.y + 170f * scale
                )
                if (!isHovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                    currentQuest = null
                    page = 0
                }

                ImGui.beginChild("Quests", 104f * scale, 136f * scale, false)

                quests.nodes.forEach {
                    if (drawEntry(it)) {
                        currentQuest = it
                    }
                }

                ImGui.endChild()

                ImGui.setCursorPos(pos.x, pos.y)
                if (currentQuest == null) {
                    ImGui.setCursorPos(pos.x + 122f * scale, pos.y + 26f * scale)
                    ImGui.image(
                        "hollowengine:textures/gui/quests/empty_menu.png".rl.toTexture().id,
                        120f * scale,
                        144f * scale,
                        0f, 0f, 1f, 1f,
                        1f, 1f, 1f, ImGui.getStyle().alpha
                    )

                    val text = "Выберите квест из списка слева для просмотра задания."
                    val size = ImGui.calcTextSize(text, true, 76f * scale)
                    ImGui.getWindowDrawList()
                        .addText(
                            ImGui.getFont(),
                            ImGui.getFontSize(),
                            ImGui.getWindowPosX() + pos.x + 182f * scale - size.x / 2,
                            ImGui.getWindowPosY() + pos.y + 98f * scale - size.y / 2,
                            ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, ImGui.getStyle().alpha),
                            text,
                            null,
                            76f * scale
                        )
                } else {
                    ImGui.setCursorPos(pos.x + 122f * scale, pos.y + 26f * scale)
                    ImGui.image(
                        "hollowengine:textures/gui/quests/quest_menu.png".rl.toTexture().id,
                        120f * scale,
                        144f * scale
                    )
                    ImGui.setCursorPos(pos.x, pos.y)

                    drawQuest(currentQuest!!)
                }
            }
        }
        ImGui.popStyleVar(5)
        ImGui.popStyleColor(6)
    }

    private fun drawQuest(questNode: QuestNode) {
        val windowPos = ImGui.getWindowPos()
        val pos = ImGui.getCursorPos()
        ImGui.setCursorPos(pos.x + 128f * scale, pos.y + 32f * scale)
        ImGui.beginChild("Description", 108f * scale, 114f * scale)

        when (page) {
            0 -> {
                ImGui.textWrapped("Автор: Иридия")
                ImGui.separator()

                val status = if(Minecraft.getInstance().player!!.inventory.contains(ItemStack(Items.NETHERITE_SWORD))) "Ожидает сдачи" else "В процессе выполнения"

                ImGui.textWrapped("Статус: $status")
                ImGui.separator()

                ImGui.textWrapped("Описание: "+ questNode.description)
            }

            1 -> {
                var i = 0
                questNode.tasks.forEach {
                    if (drawSlot(it.icon)) {
                    }
                    if ((i + 1) % 5 != 0) ImGui.sameLine()
                    i++
                }
            }

            2 -> {
                ImGui.textWrapped("Наград за это задание нет.")
            }
        }

        ImGui.endChild()

        var hovered = ImGui.isMouseHoveringRect(
            windowPos.x + 127f * scale,
            windowPos.y + 150f * scale,
            windowPos.x + 127f * scale + 25f * scale,
            windowPos.y + 150f * scale + 15f * scale
        )
        var light = if (hovered) 0.8f else 1f
        ImGui.setCursorPos(pos.x + 127f * scale, pos.y + 150f * scale)
        if (page > 0) ImGui.image(
            "hollowengine:textures/gui/trades/left_button.png".rl.toTexture().id,
            25f * scale,
            15f * scale,
            0f,
            0f,
            1f,
            1f,
            light,
            light,
            light,
            ImGui.getStyle().alpha
        )
        if (ImGui.isItemClicked() && page > 0) page--

        hovered = ImGui.isMouseHoveringRect(
            windowPos.x + 212f * scale,
            windowPos.y + 150f * scale,
            windowPos.x + 212f * scale + 25f * scale,
            windowPos.y + 150f * scale + 15f * scale
        )
        light = if (hovered) 0.8f else 1f
        ImGui.setCursorPos(pos.x + 212f * scale, pos.y + 150f * scale)
        if (page < 2) ImGui.image(
            "hollowengine:textures/gui/trades/right_button.png".rl.toTexture().id,
            25f * scale,
            15f * scale,
            0f,
            0f,
            1f,
            1f,
            light,
            light,
            light,
            ImGui.getStyle().alpha
        )
        if (ImGui.isItemClicked() && page < 2) page++

    }

    fun drawEntry(questNode: QuestNode): Boolean {
        val startPos = ImGui.getCursorScreenPos()
        val isHovered = ImGui.isMouseHoveringRect(
            startPos.x, startPos.y,
            startPos.x + 100f * scale,
            startPos.y + 22f * scale
        )
        val color = if (isHovered || questNode == currentQuest) ImVec4(1f, 1f, 1f, ImGui.getStyle().alpha)
        else ImVec4(0.8f, 0.8f, 0.8f, ImGui.getStyle().alpha)

        ImGui.image(
            "hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale,
            0f, 0f, 1f, 1f,
            color.x, color.y, color.z, color.w
        )
        ImGui.getWindowDrawList()
            .addText(
                ImGui.getFont(),
                ImGui.getFontSize(),
                startPos.x + 22f * scale,
                startPos.y + 12f * scale - ImGui.calcTextSize(questNode.title, true, 76f * scale).y / 2,
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, ImGui.getStyle().alpha),
                questNode.title,
                null,
                76f * scale
            )
        return isHovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)
    }

    fun drawSlot(item: ItemStack): Boolean {
        val inventory = Minecraft.getInstance().player?.inventory ?: return false

        val pos = ImGui.getCursorPos()
        ImGui.image("hollowengine:textures/gui/quests/slot.png".rl.toTexture().id, 18f * scale, 19f * scale)
        ImGui.setCursorPos(pos.x + scale, pos.y + scale * 2)
        Graphics.item(item, 16f * scale, 16f * scale)

        val count = inventory.countItem(item.item).coerceAtMost(item.count)
        val maxCount = item.count
        val text = "$count/$maxCount"
        val ratio = count.toFloat() / maxCount
        val size = ImGui.calcTextSize(text)
        ImGui.setCursorPos(pos.x + 17f * scale - size.x, pos.y + 18f * scale - size.y)

        ImGui.pushStyleColor(ImGuiCol.Text, 1f-ratio, ratio, 0f, ImGui.getStyle().alpha)
        Graphics.textShadow(text)
        ImGui.popStyleColor()

        ImGui.setCursorPos(pos.x, pos.y)
        ImGui.dummy(18f * scale, 19f * scale)
        return ImGui.isItemClicked(ImGuiMouseButton.Right)
    }
}
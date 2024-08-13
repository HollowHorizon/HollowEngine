package ru.hollowhorizon.hollowengine.client.gui.npcs.quests

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.nodes.itemPicker
import ru.hollowhorizon.hollowengine.common.npcs.quests.AcceptQuestPacket
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestNode
import ru.hollowhorizon.hollowengine.common.npcs.quests.tasks.CollectItemsTask
import kotlin.math.min

class QuestAcceptScreen(val npc: NPCEntity, val questNode: QuestNode, val editMode: Boolean) : ImGuiScreen() {
    private var scale = 1f
    private var imTitle = ImString()
    private var nameBuffer = ImString()
    private var description = ImString(500)

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width / 480f, window.height * 0.9f)

        val animator = npc[AnimatedEntityCapability::class]

        animator.layers.removeIf { it.animation == "offer" }
        animator.layers.add(
            AnimationLayer(
                "offer", LayerMode.ADD, PlayMode.LAST_FRAME, 1f
            )
        )
    }

    override fun ImGuiMethods.draw() {
        val window = Minecraft.getInstance().window
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())

        imgui.internal.ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)

        val fontSize = when (scale) {
            in 0f..1.5f -> 15
            in 1.5f..2f -> 24
            in 2f..2.5f -> 30
            in 2.5f..3f -> 50
            else -> 40
        }

        pushFontSize(fontSize) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 1f, 1f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)
            centredWindow {
                image(
                    "hollowengine:textures/gui/npc_menu/background.png".rl,
                    window.width.toFloat(),
                    window.height.toFloat()
                )

                drawContextMenu()
                drawNpcPreview()
            }
            ImGui.popStyleColor(2)
        }

        ImGui.popStyleVar()
    }

    private fun drawContextMenu() {
        ImGui.setCursorPos(70 * scale, 24 * scale)

        ImGui.image("hollowengine:textures/gui/quests/name.png".rl.toTexture().id, 190f * scale, 20f * scale)
        ImGui.setCursorPos(77 * scale, 30 * scale)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 0.25f, 0.5f, 1f, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 5 * scale, scale)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0f)
        ImGui.pushStyleColor(ImGuiCol.Border, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0.82f, 0.41f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 1f, 0.5f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0.9f, 0.4f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0.164f, 0.078f, 0f, 1f)
        imTitle.set(questNode.title)
        ImGui.inputTextMultiline("##name", imTitle, 180 * scale, 12 * scale, ImGuiInputTextFlags.CtrlEnterForNewLine)
        questNode.title = imTitle.get()


        ImGui.setCursorPos(70 * scale, 45 * scale)

        ImGui.image("hollowengine:textures/gui/quests/description.png".rl.toTexture().id, 190f * scale, 98f * scale)

        ImGui.setCursorPos(87 * scale, 60 * scale)

        description.set(questNode.description)
        ImGui.inputTextMultiline("##description", description, 157 * scale, 72 * scale)
        questNode.description = description.get()


        ImGui.setCursorPos(70 * scale, 144 * scale)
        ImGui.image("hollowengine:textures/gui/quests/panel.png".rl.toTexture().id, 93f * scale, 89f * scale)
        var size = ImGui.calcTextSize("Задание").times(0.5f, 0.5f)
        ImGui.setCursorPos(116.5f * scale - size.x, 155 * scale - size.y)
        ImGuiMethods.textShadow("Задание")

        ImGui.setCursorPos(73 * scale, 163 * scale)
        ImGui.beginChild("#tasks", 87f * scale, 64f * scale, true)
        drawTasks()
        ImGui.endChild()

        ImGui.setCursorPos(167 * scale, 144 * scale)
        ImGui.image("hollowengine:textures/gui/quests/panel.png".rl.toTexture().id, 93f * scale, 89f * scale)
        size = ImGui.calcTextSize("Награды").times(0.5f, 0.5f)
        ImGui.setCursorPos(213.5f * scale - size.x, 155 * scale - size.y)
        ImGuiMethods.textShadow("Награды")

        ImGui.setCursorPos(170 * scale, 163 * scale)
        ImGui.beginChild("#rewards", 87f * scale, 64f * scale, true)
        drawRewards()
        ImGui.endChild()

        ImGui.popStyleVar(3)
        ImGui.popStyleColor(9)

        if(!editMode) {
            ImGui.setCursorPos(70 * scale, 234 * scale)
            if (button("accept")) {
                AcceptQuestPacket(this.questNode).send()
                val animator = npc[AnimatedEntityCapability::class]
                animator.layers.removeIf { it.animation == "offer" }
                super.onClose()
            }
            if(ImGui.isItemHovered()) ImGui.setTooltip("Принять квест")
            ImGui.sameLine()
            if (button("cancel")) {
                onClose()
            }
            if(ImGui.isItemHovered()) ImGui.setTooltip("Отказаться от задания")
        }
    }

    fun drawTasks() {
        var i = 0
        questNode.tasks.forEach {
            if (drawSlot(it.icon)) {
                ImGui.openPopup("quest_editor")
                imgui.internal.ImGui.getStateStorage().setInt(imgui.internal.ImGui.getID("task_id"), i)
            }
            if ((i + 1) % 4 != 0) ImGui.sameLine()
            i++
        }
        if (i > 0 && i % 4 != 0) ImGui.sameLine()
        if (editMode && drawAddSlot()) questNode.tasks.add(CollectItemsTask())

        if (ImGui.isPopupOpen("quest_editor")) {
            val nodeId = ImGui.getStateStorage().getInt(imgui.internal.ImGui.getID("task_id"))

            ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 2f)
            ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 10f)
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 2 * scale, 2 * scale)
            ImGui.pushStyleColor(ImGuiCol.Border, 1f, 1f, 1f, 1f)
            ImGui.pushStyleColor(ImGuiCol.PopupBg, 0f, 0f, 0f, 0.6f)
            if (ImGui.beginPopup("quest_editor")) {
                val task = questNode.tasks[nodeId]
                if (ImGui.beginMenu("Установить предмет")) {
                    Minecraft.getInstance().player!!.inventory.itemPicker {
                        task.icon = it
                        ImGui.closeCurrentPopup()
                    }
                    ImGui.endMenu()
                }
                ImGui.separator()
                if (ImGui.beginMenu("Переименовать")) {
                    nameBuffer.set(task.name)
                    ImGuiMethods.textShadow("Название: "); ImGui.sameLine()

                    imgui.internal.ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
                    imgui.internal.ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)
                    ImGui.inputText("##task_name", nameBuffer)
                    imgui.internal.ImGui.popStyleVar(2)

                    task.name = nameBuffer.get()
                    ImGui.endMenu()
                }

                ImGui.separator()
                if (ImGui.menuItem("Удалить задание")) {
                    questNode.tasks.removeAt(nodeId)
                    ImGui.closeCurrentPopup()
                }

                ImGui.endPopup()
            }
            ImGui.popStyleVar(3)
            ImGui.popStyleColor(2)
        }
    }

    private fun drawAddSlot(): Boolean {
        val pos = ImGui.getCursorScreenPos()
        val hovered = ImGui.isMouseHoveringRect(pos.x, pos.y, pos.x + 18 * scale, pos.y + 18 * scale)
        val color = if (hovered) 0.75f else 1f
        ImGui.image(
            "hollowengine:textures/gui/quests/slot_add.png".rl.toTexture().id,
            18f * scale,
            19f * scale,
            0f,
            0f,
            1f,
            1f,
            color,
            color,
            color,
            1f
        )
        return ImGui.isItemClicked()
    }

    fun drawRewards() {
    }

    fun drawSlot(item: ItemStack): Boolean {
        val pos = ImGui.getCursorPos()
        ImGui.image("hollowengine:textures/gui/quests/slot.png".rl.toTexture().id, 18f * scale, 19f * scale)
        ImGui.setCursorPos(pos.x + scale, pos.y + scale * 2)
        ImGuiMethods.item(item, 16f * scale, 16f * scale)
        ImGui.setCursorPos(pos.x, pos.y)
        ImGui.dummy(18f * scale, 19f * scale)
        return ImGui.isItemClicked(ImGuiMouseButton.Right)
    }

    fun button(image: String): Boolean {
        val pos = ImGui.getCursorScreenPos()
        val isHovered = ImGui.isMouseHoveringRect(
            pos.x, pos.y, pos.x + 22 * scale, pos.y + 24 * scale
        )
        ImGuiMethods.image(
            "hollowengine:textures/gui/quests/$image.png".rl,
            22 * scale,
            24 * scale,
            22 * scale,
            48 * scale,
            v0 = if (isHovered) 24 * scale else 0f,
            v1 = if (isHovered) 48 * scale else 24 * scale
        )
        return ImGui.isItemClicked()
    }

    fun drawNpcPreview() {
        ImGui.setCursorPos(334 * scale, 40 * scale)

        ImGui.image("hollowengine:textures/gui/npc_menu/nickname.png".rl.toTexture().id, 90f * scale, 20f * scale)

        val size = ImGui.calcTextSize(npc.name.string)
        ImGui.setCursorPos(379 * scale - size.x / 2, 50 * scale - size.y / 2)
        ImGuiMethods.textShadow(npc.name.string)

        ImGui.setCursorPos(320f * scale, 63 * scale)

        ImGui.image("hollowengine:textures/gui/npc_menu/character.png".rl.toTexture().id, 118f * scale, 155f * scale)
        ImGui.setCursorPos(331f * scale, 76 * scale)
        ImGuiMethods.entity(
            npc,
            96 * scale,
            136 * scale,
            scale = 1.25f,
            offsetY = 50 * scale,
            alpha = imgui.internal.ImGui.getStyle().alpha,
            rotation = false
        )
    }

    override fun onClose() {
        QuestsGraphGui(npc, editMode).open()

        val animator = npc[AnimatedEntityCapability::class]
        animator.layers.removeIf { it.animation == "offer" }
    }
}
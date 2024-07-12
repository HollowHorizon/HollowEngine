package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.SkinDownloader
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import kotlin.math.min

class QuestsGui : ImGuiScreen() {
    private var scale = 1f

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width * 0.75f / 242f, window.height * 0.75f / 170f)
    }

    override fun ImGuiMethods.draw() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, scale*2, scale*2)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 3f*scale)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0.82f, 0.41f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 1f, 0.5f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0.9f, 0.4f, 0f, 1f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0f, 0f, 0f, 0f)
        centredWindow {
            val pos = ImGui.getCursorPos()
            ImGui.image(
                "hollowengine:textures/gui/quests/quests_menu.png".rl.toTexture().id,
                242f * scale,
                170f * scale
            )

            ImGui.setCursorPos(pos.x + 35 * scale, pos.y + 4 * scale)
            pushFontSize((100 * scale / 6.511f).toInt()) {
                textShadow("Квесты")
                ImGui.sameLine()
                ImGui.setCursorPosX(pos.x + 155 * scale)
                textShadow("Описание")
            }

            ImGui.setCursorPos(pos.x + 7 * scale, pos.y + 30 * scale)

            ImGui.beginChild("Quests", 106f * scale, 136f * scale)

            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)
            ImGui.image("hollowengine:textures/gui/quests/quest.png".rl.toTexture().id, 100f * scale, 22f * scale)


            ImGui.endChild()
        }
        ImGui.popStyleVar(5)
        ImGui.popStyleColor(5)
    }
}
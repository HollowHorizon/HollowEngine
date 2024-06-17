package ru.hollowhorizon.hollowengine.client.gui.npcs

import imgui.ImGui
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorContext
import imgui.extension.texteditor.TextEditor
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.utils.mcText

class NpcBehaviorGui : Screen("".mcText) {
    val entries = arrayListOf(
        "Мой слой 1", "Какие-то ебучие ноды", "Ахуенные скрипты kts"
    )
    val editor = TextEditor()
    val ctx = NodeEditorContext()
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImguiHandler.drawFrame {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)

            val window = Minecraft.getInstance().window
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())
            ImGuiMethods.window(
                "Поведение персонажа", ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or
                        ImGuiWindowFlags.NoTitleBar
            ) {
                when(entries[BehaviorLayers.selected]) {
                    "Какие-то ебучие ноды" -> {
                        NodeEditor.setCurrentEditor(ctx)
                        NodeEditor.begin("Behavior")
                        NodeEditor.end()
                    }

                    "Ахуенные скрипты kts" -> {
                        editor.render("fun main() {\n    println(\"Hello World!\")\n}")
                    }

                    "Мой слой 1" -> {
                        text("Здесь вы можете настроить свой интерфейс, например:")
                        ImGui.separator()
                        ImGui.newLine()
                        text("Введите игровое время для запуска:")
                        ImGui.inputInt("Время", ImInt())
                        ImGui.separator()
                        text("Введите блоки для обхода:")
                        ImGui.inputFloat3("x, y, z", floatArrayOf(0f, 0f, 0f))
                        ImGui.inputFloat3("x, y, z", floatArrayOf(0f, 0f, 0f))
                        ImGui.inputFloat3("x, y, z", floatArrayOf(0f, 0f, 0f))
                        ImGui.inputFloat3("x, y, z", floatArrayOf(0f, 0f, 0f))
                        ImGui.button("Добавить точку")
                        ImGui.separator()
                        text("Введите скрипт при достижении точки")
                        ImGui.inputText("Скрипт", ImString())
                    }
                }

                BehaviorLayers.draw(entries)
            }

            ImGui.popStyleVar()
        }
    }
}
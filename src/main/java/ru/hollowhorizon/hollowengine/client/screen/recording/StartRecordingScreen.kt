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

package ru.hollowhorizon.hollowengine.client.screen.recording

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.api.AutoScaled
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.cutscenes.replay.ToggleRecordingPacket

class StartRecordingScreen : HollowScreen(), AutoScaled {
    private val fileName = ImString()
    private val modelName = ImString()

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pPoseStack)
        ImguiHandler.drawFrame {
            centredWindow(args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize) {

                ImGui.text("Создание нового реплея")
                ImGui.separator()
                ImGui.inputText("Имя реплея", fileName)
                ImGui.inputText("Путь к модели персонажа", modelName)
                if(ImGui.isItemHovered() && Screen.hasControlDown()) ImGui.setTooltip("Модель персонажа который будет проигрываться в реплее.")

                if (ImGui.button("Начать")) {
                    startRecording(fileName.get(), modelName.get())
                    onClose()
                }
                ImGui.sameLine()
                if (ImGui.button("Отмена")) onClose()
            }
        }
    }
}

fun startRecording(replayName: String, modelName: String) {
    Minecraft.getInstance().player!![AnimatedEntityCapability::class].model = modelName
    ToggleRecordingPacket(replayName).send()
    RecordingDriver.resetTime()
    RecordingDriver.enable = true
}
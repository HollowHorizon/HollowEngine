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
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.api.AutoScaled
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.cutscenes.replay.ToggleRecordingPacket

class ModifyRecordingScreen : HollowScreen(), AutoScaled {
    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pPoseStack)
        ImguiHandler.drawFrame {
            centredWindow(args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize) {
                val size = ImGui.calcTextSize("Редактирование реплея")
                ImGui.text("Редактирование реплея")
                ImGui.separator()
                if (ImGui.button("Запустить анимацию", size.x, size.y)) PlayAnimationScreen().open()
                if (ImGui.button("Остановить анимацию", size.x, size.y)) StopAnimationScreen().open()
                if (ImGui.button("Завершить реплей", size.x, size.y)) {
                    RecordingDriver.enable = false
                    Minecraft.getInstance().player!![AnimatedEntityCapability::class].model = "%NO_MODEL%"
                    Minecraft.getInstance().player!![AnimatedEntityCapability::class].animations.clear()
                    Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers.clear()
                    ToggleRecordingPacket("").send()
                    onClose()
                }
                if (ImGui.button("Пауза", size.x, size.y)) onClose()
                if (ImGui.button("Отмена", size.x, size.y)) {
                    RecordingDriver.enable = true
                    onClose()
                }
            }
        }
    }
}

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
import imgui.type.ImInt
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.api.AutoScaled
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.cutscenes.replay.PauseRecordingPacket
import ru.hollowhorizon.hollowengine.cutscenes.replay.RecordingContainer

class PlayAnimationScreen : HollowScreen(), AutoScaled {
    private val animations =
        GltfManager.getOrCreate(Minecraft.getInstance().player!![AnimatedEntityCapability::class].model.rl).modelTree.animations
            .map { it.name ?: "Unnamed" }.toTypedArray()
    private val layers = arrayOf("Добавочный", "Перезапись")
    private val playModes = arrayOf("Одиночный", "Цикл", "Посл. кадр", "Обратный")
    private val animation = ImInt()
    private val layer = ImInt()
    private val playMode = ImInt()
    private val speed = FloatArray(1) { 1.0f }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: kotlin.Float) {
        renderBackground(pPoseStack)
        ImguiHandler.drawFrame {
            centredWindow(args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize) {
                ImGui.text("Запуск анимации реплея")
                ImGui.separator()

                ImGui.combo("Анимация", animation, animations)
                ImGui.combo("Слой", layer, layers)
                ImGui.combo("Режим", playMode, playModes)
                ImGui.sliderFloat("Скорость", speed, 0.01f, 100f, "%.2f")

                if (ImGui.button("Применить")) {
                    val anim = animations[animation.get()]
                    val layer = LayerMode.entries[layer.get()]
                    val mode = PlayMode.entries[playMode.get()]
                    PauseRecordingPacket(
                        true, RecordingContainer(
                            anim, layer, mode, speed[0]
                        )
                    ).send()
                    Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers += AnimationLayer(
                        anim,
                        layer,
                        mode,
                        speed[0]
                    )
                    RecordingDriver.enable = true
                    onClose()
                }
                ImGui.sameLine()
                if (ImGui.button("Отмена")) onClose()
            }
        }
    }
}

class StopAnimationScreen : HollowScreen() {
    private val animations =
        GltfManager.getOrCreate(Minecraft.getInstance().player!![AnimatedEntityCapability::class].model.rl).modelTree.animations
            .map { it.name ?: "Unnamed" }.toTypedArray()
    private val animation = ImInt()

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: kotlin.Float) {
        renderBackground(pPoseStack)
        ImguiHandler.drawFrame {
            centredWindow(args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize) {
                ImGui.text("Остановка анимации реплея")
                ImGui.separator()

                ImGui.combo("Анимация", animation, animations)

                if (ImGui.button("Применить")) {
                    val anim = animations[animation.get()]
                    val animation = "%STOP%$anim"

                    PauseRecordingPacket(
                        true, RecordingContainer(
                            animation, LayerMode.ADD, PlayMode.ONCE, 1.0f
                        )
                    ).send()
                    RecordingDriver.enable = true
                    Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers.removeIf { it.animation == anim }
                    onClose()
                }
                ImGui.sameLine()
                if (ImGui.button("Отмена")) onClose()
            }
        }
    }
}
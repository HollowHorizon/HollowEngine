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

package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager

class CodeEditorGui : HollowScreen() {

    override fun init() {
        RequestTreePacket().send()
    }

    var shouldClose = false
    var loadSettings = true

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        val file = DirectoryManager.HOLLOW_ENGINE.resolve(".gui_cache/code_editor.halva")
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        if(!file.exists()) file.createNewFile()
        ImguiHandler.drawFrame {
            if (loadSettings) {
                loadSettings = false
                ImGui.loadIniSettingsFromMemory(file.readText())
            }
            IDEGui.draw()
            file.writeText(ImGui.saveIniSettingsToMemory())

            if (shouldClose) onClose()
        }
    }

    override fun onClose() {
        if (!shouldClose) {
            IDEGui.shouldClose = true
            shouldClose = true
            return
        }

        super.onClose()
        IDEGui.onClose()
        shouldClose = false
    }

    override fun isPauseScreen() = false
}

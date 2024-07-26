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

package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.extension.imnodes.ImNodes
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import ru.hollowhorizon.hollowengine.client.gui.npcs.ScriptNodeEditor
import ru.hollowhorizon.hollowengine.client.translate
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability

class NPCToolGui(val npc: NPCEntity) : HollowScreen() {
    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        ImguiHandler.drawFrame {
            val window = Minecraft.getInstance().window
            ImGui.setNextWindowSize(window.width * 0.9f, window.height * 0.9f)
            centredWindow {
                if (imageButton("wrench", "npctool.$MODID.npc.setting".translate)) {
                    NPCCreatorGui(npc, npc.id).open()
                }
                sameLine()
                if (imageButton("nodes", "npctool.$MODID.npc.nodes".translate)) {
                    ScriptNodeEditor(npc).open()
                }
                //sameLine()
//              //imageButton("pose", "npctool.$MODID.npc.pose".translate)
            }
        }
    }

    fun imageButton(image: String, desc: String): Boolean {
        val isClicked = ImGui.imageButton("hollowengine:textures/gui/icons/$image.png".rl.toTexture().id, 256f, 256f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(desc)
        return isClicked
    }
}

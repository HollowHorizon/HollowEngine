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

package ru.hollowhorizon.hollowengine.client.gui.npcs

import com.mojang.blaze3d.vertex.PoseStack
import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods.centredWindow
import ru.hollowhorizon.hc.client.imgui.ImguiHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.NodeEditor
import ru.hollowhorizon.hollowengine.client.gui.NodeEditorV2
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph

class ScriptNodeEditor(val npc: NPCEntity) : HollowScreen() {
    private val graph = npc[NPCCapability::class.java].script

    override fun init() {
        super.init()
        NodeEditor.isLoaded = false
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        ImguiHandler.drawFrame {
            val window = Minecraft.getInstance().window
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(window.width.toFloat(), window.height.toFloat())
            centredWindow(
                args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or
                        ImGuiWindowFlags.NoBackground
            ) {
                NodeEditorV2.draw(graph)
            }
            ImGui.popStyleVar()
        }
    }

    override fun onClose() {
        super.onClose()
        graph.activeNodes.clear()
        SaveNodesPacket(graph.serializeNBT(), npc.id).send()
    }
}

@Serializable
@HollowPacketV2
class SaveNodesPacket(val graph: @Serializable(ForCompoundNBT::class) CompoundTag, val npcId: Int) :
    HollowPacketV3<SaveNodesPacket> {
    override fun handle(player: Player, data: SaveNodesPacket) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас нет прав на редактирование скриптов!".mcText)
        }
        val npc = player.level.getEntity(npcId) as? NPCEntity ?: return
        val graph = ScriptGraph()
        graph.init(npc)
        graph.deserializeNBT(this.graph)
        graph.restart()
        npc[NPCCapability::class].script = graph
    }

}
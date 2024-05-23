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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.dialogues

import imgui.ImGui
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.inputPins
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.outPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.DialoguePin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.NodePin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.StringPin
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.*

class ChoiceNode() : DialogueNode() {
    val input by inPin<DialogueNode, DialoguePin>()
    var isStarted = false
    var index = -1

    override fun tick(graph: ScriptGraph) {
        val npc = graph.npc
        val server = npc.server ?: return

        if (!isStarted) {
            isStarted = true
            val sublist = inputs.subList(1, inputs.size).map { it.value as String }.toMutableList()
            SERVER_OPTIONS.update(server.playerList.players) {
                this.choices.clear()
                this.choices.addAll(sublist)
            }
            MinecraftForge.EVENT_BUS.register(this)
        }
        if (index != -1) {
            val nextNodes = outputs[index].inputPins.map { it.value as ScriptNode }.onEach { it.start(graph) }
            if (nextNodes.all { it !is DialogueNode }) DialogueScreenPacket(
                false,
                canClose = true
            ).send(*graph.npc.server!!.playerList.players.toTypedArray())
            graph.activeNodes.remove(this)
            graph.activeNodes.addAll(nextNodes)
            MinecraftForge.EVENT_BUS.unregister(this)
            isStarted = false
            index = -1
        }
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        index = event.choice
    }

    override fun draw(graph: ScriptGraph) {
        if (ImGui.button("+")) addPin()
        ImGui.sameLine()
        if (outputs.size > 0) if (ImGui.button("-")) removePin()
    }

    private fun addPin() {
        inPin<String, StringPin>()
        outPin<ScriptNode, NodePin>()
    }

    private fun removePin() {
        if (outputs.isNotEmpty()) {
            outputs.removeLast()
            inputs.removeLast()
        }
    }
}
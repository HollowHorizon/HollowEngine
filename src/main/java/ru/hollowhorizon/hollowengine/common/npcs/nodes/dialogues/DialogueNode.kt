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

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.client.screen.DialogueOptions
import ru.hollowhorizon.hollowengine.common.network.ServerMouseClickedEvent
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.inputPins
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.outPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.DialoguePin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.NodePin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.StringPin
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.DialogueScreenPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.SERVER_OPTIONS
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.update

open class DialogueNode : ScriptNode()

class OpenDialogueNode() : DialogueNode() {
    val parent by inPin<ScriptNode, NodePin>()
    val next by outPin<DialogueNode, DialoguePin>()

    override fun tick(graph: ScriptGraph) {
        DialogueScreenPacket(true, canClose = true).send(*graph.npc.server!!.playerList.players.toTypedArray())
        complete(graph)
    }

    fun complete(graph: ScriptGraph) {
        SERVER_OPTIONS = DialogueOptions()
        graph.activeNodes.remove(this)
        outputs[0].inputPins.map { it.value }.forEach {
            graph.activeNodes.add(it as ScriptNode)
        }
    }
}

class CloseDialogueNode() : DialogueNode() {
    val parent by inPin<DialogueNode, DialoguePin>()
    val next by outPin<ScriptNode, NodePin>()

    override fun tick(graph: ScriptGraph) {
        DialogueScreenPacket(false, canClose = false).send(*graph.npc.server!!.playerList.players.toTypedArray())
        complete(graph)
    }

    fun complete(graph: ScriptGraph) {
        graph.activeNodes.remove(this)
        outputs[0].inputPins.map { it.value }.forEach {
            graph.activeNodes.add(it as ScriptNode)
        }
    }
}

open class DialogueOperationNode() : DialogueNode() {
    val parent by inPin<DialogueNode, DialoguePin>()
    val next by outPin<DialogueNode, DialoguePin>()

    fun complete(graph: ScriptGraph) {
        graph.activeNodes.remove(this)
        outputs[0].inputPins.map { it.value }.forEach {
            graph.activeNodes.add(it as ScriptNode)
        }
    }
}

class DialogueSayNode() : DialogueOperationNode() {
    var name by inPin<String, StringPin>().apply {
        name = "Имя"
    }
    var text by inPin<String, StringPin>().apply {
        name = "Сообщение"
    }
    var isStarted = false
    var complete = false

    override fun tick(graph: ScriptGraph) {
        val npc = graph.npc
        val server = npc.server ?: return
        if (!isStarted) {
            isStarted = true
            SERVER_OPTIONS.update(server.playerList.players) {
                this.text = this@DialogueSayNode.text.mcText
                this.name = this@DialogueSayNode.name.mcText
                if (npc !in characters) characters.add(npc)
            }
            MinecraftForge.EVENT_BUS.register(this)
        }
        if (complete) {
            complete(graph)
            isStarted = false
            complete = false
            MinecraftForge.EVENT_BUS.unregister(this)
        }
    }

    @SubscribeEvent
    fun onEvent(event: ServerMouseClickedEvent) {
        complete = true
    }
}
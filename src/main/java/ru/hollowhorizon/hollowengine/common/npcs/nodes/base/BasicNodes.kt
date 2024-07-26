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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.connections.inputPins
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.inPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.outPin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.*
import ru.hollowhorizon.hollowengine.common.scripting.StoryLogger

class StartNode : ScriptNode() {
    val next by outPin<ScriptNode, NodePin>()

    override fun tick(graph: ScriptGraph) {
        graph.activeNodes.remove(this)
        outputs[0].inputPins.map { it.self() }.forEach {
            graph.activeNodes.add((it as ScriptNode).apply { start(graph)})
        }
    }
}

class EndNode: ScriptNode() {
    val parent by inPin<ScriptNode, NodePin>()

    override fun tick(graph: ScriptGraph) {
        graph.activeNodes.clear()
    }
}

open class OperationNode: ScriptNode() {
    val prev by inPin<ScriptNode, NodePin>()
    val next by outPin<ScriptNode, NodePin>()

    val hasNext get() = outputs[0].isConnected

    open fun complete(graph: ScriptGraph) {
        graph.activeNodes.remove(this)
        outputs[0].inputPins.map { it.self() }.forEach {
            graph.activeNodes.add((it as ScriptNode).apply { start(graph)})
        }
    }
}

class IfNode: OperationNode() {
    val then by outPin<ScriptNode, NodePin>()
    val condition by inPin<Boolean, BooleanPin>().apply {
        name = "Условие"
    }

    override fun tick(graph: ScriptGraph) {
        if (condition) {
            complete(graph)
        } else {
            graph.activeNodes.remove(this)
            outputs[1].inputPins.map { it.self() }.forEach {
                graph.activeNodes.add(it as ScriptNode).apply { start(graph)}
            }
        }
    }
}

class WaitNode: OperationNode() {
    val count by inPin<Int, IntPin>().apply {
        name = "Время в тиках"
    }
    var time = -1

    override fun tick(graph: ScriptGraph) {
        if (time == -1) time = count

        if (time-- == 0) {
            time = -1
            complete(graph)
        }
    }

    override fun serialize(tag: CompoundTag) {
        super.serialize(tag)
        tag.putInt("current_time", time)
    }

    override fun deserialize(tag: CompoundTag) {
        super.deserialize(tag)
        time = tag.getInt("current_time")
    }
}

class CommandNode: OperationNode() {
    val command by inPin<String, StringPin>()

    override fun tick(graph: ScriptGraph) {
        val server = graph.npc.server ?: return
        val src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()

        if (server.commands.performPrefixedCommand(src, command) == 0) {
            StoryLogger.LOGGER.warn("Command \"${command}\" execution failed!")
        }

        complete(graph)
    }
}
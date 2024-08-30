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

package ru.hollowhorizon.hollowengine.common.npcs.connections

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.nodes.ScriptNode
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pin

class Connection(
    val input: ScriptNode,
    val output: ScriptNode,
    val outputPin: Pin<*>,
    val inputPin: Pin<*>,
)

fun Connection.toTag(graph: ScriptGraph) = CompoundTag().apply {
    putShort("input", graph.nodes.indexOf(input).toShort())
    putShort("output", graph.nodes.indexOf(output).toShort())
    putShort("outputPin", input.outputs.findIndex { it === outputPin }.toShort())
    putShort("inputPin", output.inputs.findIndex { it === inputPin }.toShort())
}

fun CompoundTag.toConnection(graph: ScriptGraph): Connection {
    val input = graph.nodes.elementAt(getShort("input").toInt())
    val output = graph.nodes.elementAt(getShort("output").toInt())
    val inputPin = input.outputs.elementAt(getShort("outputPin").toInt())
    val outputPin = output.inputs.elementAt(getShort("inputPin").toInt())
    return Connection(input, output, inputPin, outputPin)
}

fun <T> List<T>.findIndex(predicate: (T) -> Boolean): Int {
    for ((index, element) in this.withIndex()) {
        if (predicate(element)) return index
    }
    return -1
}
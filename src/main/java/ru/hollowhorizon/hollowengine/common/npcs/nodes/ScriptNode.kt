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

package ru.hollowhorizon.hollowengine.common.npcs.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.npcs.ScriptGraph
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.Pin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.deserializePin
import ru.hollowhorizon.hollowengine.common.npcs.nodes.pins.serialize
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import ru.hollowhorizon.hollowengine.common.registry.RegistryEntry
import ru.hollowhorizon.hollowengine.common.util.deserialize
import ru.hollowhorizon.hollowengine.common.util.serialize

open class ScriptNode: RegistryEntry {
    val inputs = ArrayList<Pin<*>>()
    val outputs = ArrayList<Pin<*>>()
    override lateinit var type: ResourceLocation

    open fun start(graph: ScriptGraph) {}
    open fun draw(graph: ScriptGraph) {}
    open fun drawPost(graph: ScriptGraph) {}
    open fun tick(graph: ScriptGraph) {}

    open fun serialize(tag: CompoundTag) {}
    open fun deserialize(tag: CompoundTag) {}

    fun isLoaded(): Boolean = inputs.all { it.isLoaded() } && outputs.all { it.isLoaded() }
}

interface ForgeEventNode

inline fun <V, reified T : Pin<V>> ScriptNode.inPin(): T {
    val pin = PinsRegistry.find<T>()
    pin.mode = Pin.Mode.INPUT
    inputs += pin
    return pin
}

inline fun <V, reified T : Pin<V>> ScriptNode.outPin(): T {
    val pin = PinsRegistry.find<T>()
    pin.mode = Pin.Mode.OUTPUT
    outputs += pin
    return pin
}

fun ScriptNode.toTag(): CompoundTag {
    val tag = CompoundTag()
    tag.putString("type", type.toString())
    tag.put("inputs", inputs.serialize { it.serialize() })
    tag.put("outputs", outputs.serialize { it.serialize() })
    tag.put("tag", CompoundTag().apply(::serialize))
    return tag
}

fun CompoundTag.toScriptNode(): ScriptNode {
    val type = this.getString("type")
    val node: ScriptNode = NodesRegistry.find(type.rl)

    val newInputs = ArrayList<Pin<*>>()
    val newOutputs = ArrayList<Pin<*>>()
    newInputs.deserialize(getList("inputs", 10)) { (it as CompoundTag).deserializePin() }
    newOutputs.deserialize(getList("outputs", 10)) { (it as CompoundTag).deserializePin() }

    newInputs.forEachIndexed { index, pin ->
        if (index < node.inputs.size) node.inputs[index].deserializeNBT(pin.serializeNBT())
        else node.inputs.add(pin)
    }
    newOutputs.forEachIndexed { index, pin ->
        if (index < node.outputs.size) node.outputs[index].deserializeNBT(pin.serializeNBT())
        else node.outputs.add(pin)
    }

    node.deserialize(getCompound("tag"))

    return node
}
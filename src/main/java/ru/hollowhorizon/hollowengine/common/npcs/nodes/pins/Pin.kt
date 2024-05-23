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

package ru.hollowhorizon.hollowengine.common.npcs.nodes.pins

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hc.client.utils.isLogicalClient
import ru.hollowhorizon.hc.client.utils.isLogicalServer
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.npcs.CURRENT_GRAPH
import ru.hollowhorizon.hollowengine.common.npcs.connections.inputPin
import ru.hollowhorizon.hollowengine.common.npcs.connections.outputPin
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import ru.hollowhorizon.hollowengine.common.registry.RegistryEntry
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Пин - точка соединения ноды, соединяя пины разных нод можно передать данные из одной ноды в другую
 */
abstract class Pin<T> : ReadWriteProperty<Any?, T>, INBTSerializable<CompoundTag>, RegistryEntry {
    override lateinit var type: ResourceLocation

    /**
     * Режим ноды, input - принимать значение из присоединённого пина или picker'а, output - рассчитывать данные.
     */
    var mode = Mode.INPUT

    /**
     * Текущее значение ноды.
     */
    var value: T? = null

    /**
     * Выбирает на клиенте значение через интерфейс.
     */
    protected open val picker: ((T?) -> T)? = null
    open var updater: (() -> T?)? = null

    fun pick() {
        value = picker?.invoke(value)
    }

    fun self(): T? {
        update()
        return value
    }

    var name = ""
        get() = field.ifEmpty { "pins.${type.namespace}.${type.path}" }

    open val nextValue: T?
        get() {
            assert(mode == Mode.OUTPUT) { "Can't get next value from output node!" }
            return this.inputPin?.self()
        }

    open val prevValue: T?
        get() {
            assert(mode == Mode.INPUT) { "Can't get prev value from input!" }
            return this.outputPin?.self()
        }

    /**
     * Требование для запуска ноды. Пока все пины не будут готовы, нода не запустится. Это необходимо, чтобы если игрока или нпс нет на сервере, но он указан в скрипте, чтобы скрипт его дождался.
     */
    open var isLoaded = { value != null }

    protected fun update() {
        updater?.invoke()?.let { value = it }
    }


    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if(isLogicalServer) update()

        return if (mode == Mode.OUTPUT) (nextValue ?: value
        ?: throw IllegalStateException("Input Node not connected!"))
        else (prevValue ?: value ?: throw IllegalStateException("Output Node not connected!"))
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun serializeNBT() = CompoundTag().apply {
        putByte("#input", mode.ordinal.toByte())
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        mode = Mode.entries[nbt.getByte("#input").toInt()]
    }

    enum class Mode {
        INPUT, OUTPUT
    }
}

val Pin<*>.isConnected get() = CURRENT_GRAPH.connections.any { it.outputPin == this || it.inputPin == this }

val Pin<*>.selfNode
    get() = CURRENT_GRAPH.nodes.find { it.inputs.contains(this) || it.outputs.contains(this) }
        ?: throw IllegalStateException("No node found!")

fun Pin<*>.serialize() = CompoundTag().apply {
    putString("type", this@serialize.type.toString())
    put("data", this@serialize.serializeNBT())
}

fun CompoundTag.deserializePin(): Pin<*> {
    val type = getString("type")
    val data = getCompound("data")
    val pin = PinsRegistry.find<Pin<*>>(type.rl)
    pin.deserializeNBT(data)
    return pin
}
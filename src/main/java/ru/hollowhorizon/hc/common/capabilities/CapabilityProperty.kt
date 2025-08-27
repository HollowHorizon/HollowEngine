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

package ru.hollowhorizon.hc.common.capabilities

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.EndTag
import net.minecraft.world.level.block.entity.BlockEntity
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.common.utils.nbt.INBTSerializable
import ru.hollowhorizon.hc.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.common.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.common.utils.nbt.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.javaType
import kotlin.reflect.jvm.jvmErasure

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalStdlibApi::class)
open class CapabilityProperty<T : CapabilityInstance, V : Any?>(var value: V, val transferFrom: (new: V, old: V?) -> Unit = { n, o -> }) : ReadWriteProperty<T, V> {
    var defaultName = ""
    var defaultType: Class<out V>? = null

    override fun getValue(thisRef: T, property: KProperty<*>): V {
        if (defaultName.isEmpty()) {
            defaultName = property.name
            defaultType = if (value == null) property.returnType.jvmErasure.java as Class<out V> else value!!.javaClass
            if (property.name !in thisRef.notUsedTags) return value

            val tag = thisRef.notUsedTags.get(property.name) ?: return value
            if (tag is EndTag) return value

            deserialize(thisRef.notUsedTags)
        }

        return value
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
        if (defaultName.isEmpty()) defaultName = property.name
        transferFrom(value, this.value)
        this.value = value
        if (defaultType == null) defaultType =
            if (this.value == null) property.returnType.javaType as Class<out V> else this.value!!.javaClass
        thisRef.isChanged = true
        val provider = thisRef.provider
        if (provider is BlockEntity) provider.setChanged()
    }

    fun serialize(tag: CompoundTag) {
        if (defaultName.isNotEmpty() && defaultType != null) {
            when (value) {
                null -> tag.put(defaultName, EndTag.INSTANCE)
                is INBTSerializable -> tag.put(defaultName, (value as INBTSerializable).serialize())
                else -> tag.put(
                    defaultName,
                    NBTFormat.serializeNoInline(JavaHacks.forceCast(value), this.defaultType!!)
                )
            }
        }
    }

    fun deserialize(tag: CompoundTag): Boolean {
        if (defaultName.isNotEmpty() && defaultType != null && defaultName in tag) {
            try {
                if (tag[defaultName] is EndTag) value = null as V
                else if (INBTSerializable::class.java.isAssignableFrom(defaultType!!)) (value as INBTSerializable).deserialize(
                    tag[defaultName]!!
                )
                else value = (NBTFormat.deserializeNoInline(tag[defaultName]!!, this.defaultType!!) as V).apply {
                    transferFrom(this, value)
                }
                return true
            } catch (e: Exception) {
                HollowCore.LOGGER.error("Error while deserializing {}: {}", defaultType, tag[defaultName], e)
            }
        } else {
            transferFrom(value, null)
        }
        return false
    }
}

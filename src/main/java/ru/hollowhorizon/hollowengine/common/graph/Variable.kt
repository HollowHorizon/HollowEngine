package ru.hollowhorizon.hollowengine.common.graph

import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Variable<T : Any>(
    private var name: String? = null,
    private val generator: suspend () -> T,
    private val type: Class<T>,
): ReadWriteProperty<Any?, T> {
    private var value: T? = null

    suspend fun init(tag: Tag?) {
        value = tag?.let { deserialize(it) } ?: generator()
    }

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Variable<T> {
        if (name == null) name = property.name
        return this
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: error("Variable $name not initialized!")
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun serialize(): Tag? = value?.let { NBTFormat.serializeNoInline(it, type) }

    fun deserialize(tag: Tag): T = NBTFormat.deserializeNoInline(tag, type)

    fun name() = name ?: error("Variable name not found!")
}
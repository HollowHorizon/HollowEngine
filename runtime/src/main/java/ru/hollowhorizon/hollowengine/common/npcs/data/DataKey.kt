package ru.hollowhorizon.hollowengine.common.npcs.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat

class DataKey<T : Any>(
    val name: String,
    internal val serializer: KSerializer<T>,
    internal val defaultValue: (() -> T)?,
) {
    init {
        require(name.isNotBlank()) { "Data key name cannot be blank" }
    }
}

inline fun <reified T : Any> dataKey(name: String): DataKey<T> =
    DataKey(name, serializer(), null)

inline fun <reified T : Any> dataKey(name: String, noinline defaultValue: () -> T): DataKey<T> =
    DataKey(name, serializer(), defaultValue)

class NpcDataStore {
    private var tag = CompoundTag()

    fun <T : Any> get(key: DataKey<T>): T? = tag.read(key) ?: key.defaultValue?.invoke()

    fun <T : Any> getOrPut(key: DataKey<T>, defaultValue: () -> T): T {
        tag.read(key)?.let { return it }
        return defaultValue().also { set(key, it) }
    }

    fun <T : Any> getOrPut(key: DataKey<T>): T =
        getOrPut(key, key.defaultValue ?: error("Data key '${key.name}' has no default value"))

    fun <T : Any> set(key: DataKey<T>, value: T) {
        tag.write(key, value)
    }

    fun <T : Any> update(key: DataKey<T>, transform: (T) -> T): T {
        val current = get(key) ?: error("Data key '${key.name}' is not set and has no default value")
        return transform(current).also { set(key, it) }
    }

    fun contains(key: DataKey<*>): Boolean = tag.contains(key.name)

    fun remove(key: DataKey<*>): Boolean {
        val existed = contains(key)
        tag.remove(key.name)
        return existed
    }

    fun isEmpty(): Boolean = tag.isEmpty

    fun save(): CompoundTag = tag.copy()

    fun load(saved: CompoundTag) {
        tag = saved.copy()
    }
}

internal fun <T : Any> CompoundTag.read(key: DataKey<T>): T? =
    get(key.name)?.let { NBTFormat.deserialize(key.serializer, it) }

internal fun <T : Any> CompoundTag.write(key: DataKey<T>, value: T) {
    put(key.name, NBTFormat.serialize(key.serializer, value))
}

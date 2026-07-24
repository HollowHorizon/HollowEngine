package ru.hollowhorizon.hollowengine.common.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat

/**
 * A typed handle into an NBT document. A key names a slot and carries the serializer for its value,
 * so callers read and write real Kotlin types without repeating the name or the (de)serialization:
 *
 * ```kotlin
 * @Serializable data class Quest(val title: String, val progress: Int)
 * val CurrentQuest = dataKey<Quest>("mypack:quest")
 * store[CurrentQuest] = Quest("The Missing Cargo", 3)
 * ```
 *
 * The same key type backs every NBT-keyed store in the engine — NPC data, persistent story state,
 * and the observable [ru.hollowhorizon.hollowengine.common.ui.UiData] a scripted UI binds to.
 */
class DataKey<T : Any>(
    val name: String,
    internal val serializer: KSerializer<T>,
    internal val defaultValue: (() -> T)?,
) {
    init {
        require(name.isNotBlank()) { "Data key name cannot be blank" }
    }

    /** Returns a copy of this key with a (different) default value. */
    fun withDefault(default: () -> T): DataKey<T> = DataKey(name, serializer, default)
}

inline fun <reified T : Any> dataKey(name: String): DataKey<T> =
    DataKey(name, serializer(), null)

inline fun <reified T : Any> dataKey(name: String, noinline defaultValue: () -> T): DataKey<T> =
    DataKey(name, serializer(), defaultValue)

/**
 * A plain (non-observable) NBT document keyed by [DataKey]. This is the storage NPCs and persistent
 * story state use; UI uses the observable variant so a Compose surface recomposes on change.
 */
class NbtDataStore {
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

/** Reads and deserializes [key] from this compound, or null when absent. */
fun <T : Any> CompoundTag.read(key: DataKey<T>): T? =
    get(key.name)?.let { NBTFormat.deserialize(key.serializer, it) }

/** Serializes [value] and writes it under [key]. */
fun <T : Any> CompoundTag.write(key: DataKey<T>, value: T) {
    put(key.name, NBTFormat.serialize(key.serializer, value))
}

/** Serializes [value] with [key]'s serializer without writing it anywhere. */
fun <T : Any> DataKey<T>.encode(value: T): Tag = NBTFormat.serialize(serializer, value)

/** Deserializes [tag] with [key]'s serializer. */
fun <T : Any> DataKey<T>.decode(tag: Tag): T? = runCatching { NBTFormat.deserialize(serializer, tag) }.getOrNull()

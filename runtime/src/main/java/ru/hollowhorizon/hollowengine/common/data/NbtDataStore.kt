package ru.hollowhorizon.hollowengine.common.data

import net.minecraft.nbt.CompoundTag

/**
 * A plain (non-observable) NBT document keyed by [DataKey]. This is the storage behind the persistent
 * data attached to entities and players ([data]) and behind persistent story state; UI uses the
 * observable [ru.hollowhorizon.hollowengine.common.ui.UiData] instead so a Compose surface
 * recomposes on change.
 *
 * The backing compound is allocated on the first write and dropped again when the store is cleared,
 * so an untouched store costs one empty object and is skipped entirely when its owner is saved.
 */
class NbtDataStore {
    private var tag: CompoundTag? = null

    operator fun <T : Any> get(key: DataKey<T>): T? = tag?.read(key) ?: key.default()

    fun <T : Any> getOrPut(key: DataKey<T>, defaultValue: () -> T): T {
        tag?.read(key)?.let { return it }
        return defaultValue().also { set(key, it) }
    }

    fun <T : Any> getOrPut(key: DataKey<T>): T =
        getOrPut(key, key.defaultValue ?: error("Data key '${key.name}' has no default value"))

    operator fun <T : Any> set(key: DataKey<T>, value: T) {
        val target = tag ?: CompoundTag().also { tag = it }
        target.write(key, value)
    }

    fun <T : Any> update(key: DataKey<T>, transform: (T) -> T): T {
        val current = get(key) ?: error("Data key '${key.name}' is not set and has no default value")
        return transform(current).also { set(key, it) }
    }

    operator fun contains(key: DataKey<*>): Boolean = tag?.contains(key.name) == true

    fun remove(key: DataKey<*>): Boolean {
        val target = tag ?: return false
        val existed = target.contains(key.name)
        target.remove(key.name)
        return existed
    }

    fun clear() {
        tag = null
    }

    fun isEmpty(): Boolean = tag?.isEmpty != false

    fun save(): CompoundTag = tag?.copy() ?: CompoundTag()

    fun load(saved: CompoundTag) {
        tag = if (saved.isEmpty) null else saved.copy()
    }
}

package ru.hollowhorizon.hollowengine.common.ui

import androidx.compose.runtime.mutableStateMapOf
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.data.DataKey
import ru.hollowhorizon.hollowengine.common.data.decode
import ru.hollowhorizon.hollowengine.common.data.encode

/**
 * The observable NBT document a UI is bound to. It shares the [DataKey] system with NPC and story
 * state (see [ru.hollowhorizon.hollowengine.common.data.NbtDataStore]) but stores each top-level key
 * in Compose state, so reads made inside a composition subscribe per key: a server patch that
 * rewrites `quest` recomposes only what reads `quest` and leaves the rest of the screen alone.
 *
 * The server owns the authoritative copy and ships patches ([applyPatch]); a client script may also
 * write locally for state the server does not need to know about (hover targets, scroll offsets).
 */
class UiData(initial: CompoundTag = CompoundTag()) {
    private val roots = mutableStateMapOf<String, Tag>()

    init {
        replaceAll(initial)
    }

    /** Reads [key], falling back to its default; throws if the key has neither a value nor a default. */
    operator fun <T : Any> get(key: DataKey<T>): T =
        find(key) ?: key.defaultOrNull() ?: error("UI data key '${key.name}' is not set and has no default value")

    /** Reads [key] or returns null when it is absent (and has no default). */
    fun <T : Any> find(key: DataKey<T>): T? {
        val tag = roots[key.name] ?: return key.defaultOrNull()
        return key.decode(tag) ?: key.defaultOrNull()
    }

    /** Reads [key] or returns [fallback] when it is absent. */
    fun <T : Any> getOr(key: DataKey<T>, fallback: T): T = find(key) ?: fallback

    operator fun <T : Any> set(key: DataKey<T>, value: T) {
        roots[key.name] = key.encode(value)
    }

    fun <T : Any> contains(key: DataKey<T>): Boolean = key.name in roots

    fun <T : Any> remove(key: DataKey<T>) {
        roots.remove(key.name)
    }

    /**
     * Merges [patch] into the document and drops [removed] keys. Compounds merge recursively so a
     * patch only has to carry the fields that changed; every other tag type replaces wholesale.
     */
    fun applyPatch(patch: CompoundTag, removed: Collection<String> = emptyList()) {
        for (key in patch.allKeys) {
            val incoming = patch.get(key) ?: continue
            val existing = roots[key]
            roots[key] = if (existing is CompoundTag && incoming is CompoundTag) {
                existing.copy().mergeDeep(incoming)
            } else {
                incoming.copy()
            }
        }
        removed.forEach { roots.remove(it) }
    }

    fun replaceAll(tag: CompoundTag) {
        roots.clear()
        tag.allKeys.forEach { key -> tag.get(key)?.let { roots[key] = it.copy() } }
    }

    fun snapshot(): CompoundTag = CompoundTag().apply {
        roots.forEach { (key, value) -> put(key, value.copy()) }
    }

    fun clear() = roots.clear()

    private fun <T : Any> DataKey<T>.defaultOrNull(): T? = defaultValue?.invoke()
}

private fun CompoundTag.mergeDeep(patch: CompoundTag): CompoundTag {
    for (key in patch.allKeys) {
        val incoming = patch.get(key) ?: continue
        val existing = get(key)
        if (existing is CompoundTag && incoming is CompoundTag) {
            put(key, existing.copy().mergeDeep(incoming))
        } else {
            put(key, incoming.copy())
        }
    }
    return this
}

package ru.hollowhorizon.hollowengine.common.data

import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.Tag

/**
 * An NBT document keyed by [DataKey]. This is the storage behind the persistent data attached to
 * entities and players ([data]) and behind persistent story state; UI uses the observable
 * [ru.hollowhorizon.hollowengine.common.ui.UiData] instead so a Compose surface recomposes on change.
 *
 * Values are kept one tag per top-level key rather than in a single compound, so the backing map can
 * be a Compose state map on the client and reads made inside a composition subscribe per key.
 */
class NbtDataStore(private val roots: MutableMap<String, Tag> = LinkedHashMap()) {
    /**
     * Called with the name of every key whose value changed.
     */
    var onChange: ((String) -> Unit)? = null

    /** Only the keys that are synced; the common case is none, so the map is allocated on demand. */
    private var policies: MutableMap<String, Sync>? = null

    /** Rebuilt on the first read after a change rather than on every write; see [numericPaths]. */
    private var numeric: Map<String, Float>? = null

    operator fun <T : Any> get(key: DataKey<T>): T? = roots[key.name]?.let { key.decode(it) } ?: key.default()

    fun <T : Any> getOrPut(key: DataKey<T>, defaultValue: () -> T): T {
        roots[key.name]?.let(key::decode)?.let { return it }
        return defaultValue().also { set(key, it) }
    }

    fun <T : Any> getOrPut(key: DataKey<T>): T =
        getOrPut(key, key.defaultValue ?: error("Data key '${key.name}' has no default value"))

    operator fun <T : Any> set(key: DataKey<T>, value: T) {
        rememberPolicy(key)
        put(key.name, key.encode(value))
    }

    fun <T : Any> update(key: DataKey<T>, transform: (T) -> T): T {
        val current = get(key) ?: error("Data key '${key.name}' is not set and has no default value")
        return transform(current).also { set(key, it) }
    }

    operator fun contains(key: DataKey<*>): Boolean = key.name in roots

    fun remove(key: DataKey<*>): Boolean = removeName(key.name)

    fun clear() {
        if (roots.isEmpty()) return
        val names = roots.keys.toList()
        roots.clear()
        policies = null
        names.forEach { fireChange(it) }
    }

    fun isEmpty(): Boolean = roots.isEmpty()

    fun save(): CompoundTag = CompoundTag().apply {
        roots.forEach { (name, tag) -> put(name, tag.copy()) }
    }

    fun load(saved: CompoundTag) {
        val touched = LinkedHashSet(roots.keys)
        roots.clear()
        saved.allKeys.forEach { name -> saved.get(name)?.let { roots[name] = it.copy() } }
        touched += saved.allKeys
        touched.forEach { fireChange(it) }
    }

    /** The sync policies to save alongside [save]; empty when nothing in this store is synced. */
    fun syncPolicies(): Map<String, Sync> = policies?.toMap().orEmpty()

    fun loadSyncPolicies(saved: Map<String, Sync>) {
        policies = if (saved.isEmpty()) null else LinkedHashMap(saved)
    }

    /** The values a client with this much access should hold, as one compound. */
    fun syncedSnapshot(vararg audiences: Sync): CompoundTag = CompoundTag().apply {
        policies?.forEach { (name, policy) ->
            if (policy !in audiences) return@forEach
            roots[name]?.let { put(name, it.copy()) }
        }
    }

    /**
     * Applies what the server sent. [full] replaces every server-owned key at once while leaving keys
     * the client wrote for itself alone, so a baseline does not wipe local scratch.
     */
    fun applySync(changed: CompoundTag, removed: Collection<String> = emptyList(), full: Boolean = false) {
        if (full) policies?.keys?.filterNot { it in changed.allKeys }?.forEach { removeName(it) }
        removed.forEach { removeName(it) }
        changed.allKeys.forEach { name ->
            val tag = changed.get(name) ?: return@forEach
            policy(name, Sync.TRACKING)
            put(name, tag.copy())
        }
    }

    private fun put(name: String, tag: Tag) {
        if (roots[name] == tag) return
        roots[name] = tag
        fireChange(name)
    }

    private fun removeName(name: String): Boolean {
        if (roots.remove(name) == null) return false
        policies?.remove(name)
        fireChange(name)
        return true
    }

    private fun rememberPolicy(key: DataKey<*>) {
        if (key.sync == Sync.NEVER) policies?.remove(key.name) else policy(key.name, key.sync)
    }

    private fun policy(name: String, sync: Sync) {
        val target = policies ?: LinkedHashMap<String, Sync>().also { policies = it }
        target[name] = sync
    }

    fun numericPaths(): Map<String, Float> = numeric ?: buildNumericPaths().also { numeric = it }

    private fun buildNumericPaths(): Map<String, Float> {
        if (roots.isEmpty()) return emptyMap()
        val paths = HashMap<String, Float>()
        roots.forEach { (name, tag) -> flatten(paths, name, tag) }
        return paths
    }

    private fun flatten(paths: MutableMap<String, Float>, path: String, tag: Tag) {
        when (tag) {
            is NumericTag -> paths[path] = tag.asFloat
            is CompoundTag -> tag.allKeys.forEach { key -> tag.get(key)?.let { flatten(paths, "$path.$key", it) } }
            is CollectionTag<*> -> tag.forEachIndexed { index, child -> flatten(paths, "$path.$index", child) }
            else -> Unit
        }
    }

    private fun fireChange(name: String) {
        numeric = null
        onChange?.invoke(name)
    }
}

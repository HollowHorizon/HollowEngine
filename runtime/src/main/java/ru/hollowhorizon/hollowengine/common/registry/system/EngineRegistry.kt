@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package ru.hollowhorizon.hollowengine.common.registry.system

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.FabricRegistry
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.ArrayDeque
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KProperty

@JvmInline
value class Namespace(val value: String) {
    init {
        require(value.isNotEmpty()) { "Namespace cannot be empty" }
        require(value.indexOf(':') == -1) { "Namespace cannot contain ':'" }
    }

    override fun toString(): String = value
}

@JvmInline
value class Path(val value: String) {
    init {
        require(value.isNotEmpty()) { "Path cannot be empty" }
    }

    override fun toString(): String = value
}

enum class RegistryState { CONSTRUCTING, REGISTERING, BAKED, FROZEN }

data class RegistryVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<RegistryVersion> {
    override fun compareTo(other: RegistryVersion): Int =
        compareValuesBy(this, other, RegistryVersion::major, RegistryVersion::minor, RegistryVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

open class Holder<T : Any>(
    val key: ResourceLocation,
    var id: Int,
    val flags: Int = 0,
) {
    open lateinit var value: T

    operator fun getValue(any: Any?, prop: KProperty<*>): T {
        return value
    }
}

interface Registry<T : Any> : Iterable<Holder<T>> {
    val key: ResourceLocation
    val state: RegistryState
    val size: Int
    fun getId(value: T): Int
    fun getIdByLocation(location: ResourceLocation): Int? {
        val holder = getHolder(location) ?: return null
        return holder.id
    }
    fun getLocationById(id: Int): ResourceLocation? = getHolder(id)?.key
    fun getById(id: Int): T?
    fun getHolder(id: Int): Holder<T>?
    operator fun get(key: ResourceLocation): T = getOrNull(key) ?: error("No value found for $key")
    fun getOrNull(key: ResourceLocation): T?
    fun getHolder(key: ResourceLocation): Holder<T>?
    fun contains(key: ResourceLocation): Boolean
}

interface MutableRegistry<T : Any> : Registry<T> {
    val version: RegistryVersion
    fun register(key: ResourceLocation, supplier: () -> T): Holder<T>
    fun unregister(key: ResourceLocation): Boolean
    fun bake()
    fun freeze()
    fun unfreeze()
    fun unbake()
}

enum class IdPolicy { NEVER_REUSE, FILL_GAPS }

class IntObjectTable<T : Any> {
    private var dense: Array<Any?> = arrayOfNulls(64)
    private val sparse = ConcurrentHashMap<Int, T?>()

    fun get(id: Int): T? {
        if (id >= 0 && id < dense.size) {
            @Suppress("UNCHECKED_CAST")
            return dense[id] as T?
        }
        return sparse[id]
    }

    fun set(id: Int, value: T?) {
        if (id >= 0) {
            if (id >= dense.size) grow(id)
            dense[id] = value
        } else {
            sparse[id] = value
        }
    }

    private fun grow(target: Int) {
        var n = dense.size
        while (n <= target) n = n shl 1
        val bigger = arrayOfNulls<Any?>(n)
        System.arraycopy(dense, 0, bigger, 0, dense.size)
        dense = bigger
    }

    fun clear() {
        dense.fill(null)
        sparse.clear()
    }
}

class DefaultMutableRegistry<T : Any>(
    override val key: ResourceLocation,
    private val idPolicy: IdPolicy = IdPolicy.NEVER_REUSE,
    override val version: RegistryVersion = RegistryVersion(1, 0, 0),
) : MutableRegistry<T> {

    private val lock = ReentrantReadWriteLock()
    private var _state: RegistryState = RegistryState.CONSTRUCTING
    override val state: RegistryState get() = lock.read { _state }

    private val byKey = LinkedHashMap<ResourceLocation, Holder<T>>()
    private val byValueId = IdentityHashMap<T, Int>()

    private val keyToId = HashMap<ResourceLocation, Int>()
    private val idToValue = IntObjectTable<T>()

    private val queue = LinkedHashMap<ResourceLocation, Pair<Holder<T>, () -> T>>()
    private val nextId = AtomicInteger(0)
    private val freeIds = ArrayDeque<Int>()

    override val size: Int get() = lock.read { byKey.size }

    override fun register(key: ResourceLocation, supplier: () -> T): Holder<T> = lock.write {
        check(_state == RegistryState.CONSTRUCTING || _state == RegistryState.REGISTERING) {
            "Cannot register into state $_state"
        }
        _state = RegistryState.REGISTERING
        require(key !in queue && key !in byKey) { "Duplicate key: $key in registry ${this.key}" }
        val holder = Holder<T>(key, -1)
        queue[key] = holder to supplier
        return holder
    }

    override fun unregister(key: ResourceLocation): Boolean = lock.write {
        check(_state != RegistryState.FROZEN) { "Cannot unregister from frozen registry" }
        var removed = false
        if (queue.remove(key) != null) removed = true
        val holder = byKey.remove(key)
        if (holder != null) {
            val id = holder.id
            byValueId.remove(holder.value)
            // keep value in idToValue for compatibility until next bake? We'll null it now:
            idToValue.set(id, null) // will be ignored by get(); optional compaction on next bake
            if (idPolicy == IdPolicy.FILL_GAPS) freeIds.addLast(id)
            removed = true
        }
        removed
    }

    private fun claimId(): Int = when (idPolicy) {
        IdPolicy.NEVER_REUSE -> nextId.getAndIncrement()
        IdPolicy.FILL_GAPS -> if (freeIds.isNotEmpty()) freeIds.removeFirst() else nextId.getAndIncrement()
    }

    override fun bake() = lock.write {
        if (_state == RegistryState.BAKED || _state == RegistryState.FROZEN) return
        check(_state == RegistryState.REGISTERING || _state == RegistryState.CONSTRUCTING) {
            "Bake only allowed from CONSTRUCTING/REGISTERING; was $_state"
        }
        if (queue.isEmpty()) {
            _state = RegistryState.BAKED
            return
        }
        for ((k, pair) in queue) {
            val (holder, supplier) = pair
            val value = supplier()
            val id = keyToId[k] ?: claimId().also { keyToId[k] = it }
            idToValue.set(id, value)
            byValueId[value] = id
            holder.id = id
            holder.value = value
            byKey[k] = holder
        }
        queue.clear()
        _state = RegistryState.BAKED
    }

    override fun freeze() = lock.write {
        // After freezing, registry becomes immutable: reads are lock-free (read lock only) and arrays are stable
        check(_state == RegistryState.BAKED || _state == RegistryState.REGISTERING) { "Freeze only after bake; was $_state" }
        // Ensure all queued are baked
        if (queue.isNotEmpty()) bake()
        _state = RegistryState.FROZEN
    }

    override fun unfreeze() = lock.write {
        check(_state == RegistryState.FROZEN) { "Can only unfreeze from FROZEN" }
        _state = RegistryState.BAKED
    }

    override fun unbake() = lock.write {
        check(_state == RegistryState.BAKED || _state == RegistryState.FROZEN) {
            "Can only unbake from BAKED/FROZEN"
        }

        for ((k, holder) in byKey) {
            queue[k] = holder to { holder.value }
        }
        byKey.clear()
        byValueId.clear()
        idToValue.clear()
        _state = RegistryState.REGISTERING
    }

    override fun getId(value: T): Int = lock.read { byValueId[value] ?: -1 }

    override fun getById(id: Int): T? = lock.read { idToValue.get(id) }

    override fun getHolder(id: Int): Holder<T>? = lock.read {
        val v = idToValue.get(id) ?: return null
        val k = byKey.entries.firstOrNull { it.value.id == id }?.key ?: return null
        Holder<T>(k, id).also { it.value = v }
    }

    override fun getOrNull(key: ResourceLocation): T? = lock.read { byKey[key]?.value }

    override fun getHolder(key: ResourceLocation): Holder<T>? = lock.read { byKey[key] }

    override fun contains(key: ResourceLocation): Boolean = lock.read { key in byKey || key in queue }

    override fun iterator(): Iterator<Holder<T>> = lock.read { byKey.values.toList().iterator() }
}

class DeferredRegister<T : Any>(
    private val registry: MutableRegistry<T>,
    private val namespace: String,
) {
    fun register(path: String, supplier: () -> T): ResourceLocation {
        val k = "$namespace:$path".rl
        registry.register(k, supplier)
        return k
    }
}

object RegistryManager {
    private val registries = ConcurrentHashMap<ResourceLocation, MutableRegistry<*>>()

    fun <T : Any> create(
        key: ResourceLocation,
        idPolicy: IdPolicy = IdPolicy.NEVER_REUSE,
        version: RegistryVersion = RegistryVersion(1, 0, 0),
    ): MutableRegistry<T> {
        @Suppress("UNCHECKED_CAST")
        return registries.computeIfAbsent(key) {
            DefaultMutableRegistry<T>(key, idPolicy, version)
        } as MutableRegistry<T>
    }

    fun <T : Any> create(key: net.minecraft.core.Registry<T>): MutableRegistry<T> {
        return FabricRegistry(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: ResourceLocation): MutableRegistry<T>? = registries[key] as MutableRegistry<T>?

    fun all(): Map<ResourceLocation, MutableRegistry<*>> = registries

    fun bakeAll() {
        registries.values.forEach { it.bake() }
    }

    fun freezeAll() {
        registries.values.forEach { it.freeze() }
    }
}

data class RegistrySnapshot<T : Any>(
    val registry: ResourceLocation,
    val version: RegistryVersion,
    val entries: List<Holder<T>>,
)

fun <T : Any> MutableRegistry<T>.snapshot(): RegistrySnapshot<T> =
    RegistrySnapshot(key, (this as? DefaultMutableRegistry<T>)?.version ?: RegistryVersion(1, 0, 0), this.toList())

fun <T : Any> diff(old: RegistrySnapshot<T>, now: RegistrySnapshot<T>): RegistryDiff<T> {
    val oldMap = old.entries.associateBy { it.key }
    val nowMap = now.entries.associateBy { it.key }
    val added = nowMap.keys - oldMap.keys
    val removed = oldMap.keys - nowMap.keys
    val changed = nowMap.keys.intersect(oldMap.keys).filter { k -> oldMap[k]!!.id != nowMap[k]!!.id }
    return RegistryDiff(
        added.map { nowMap[it]!! },
        removed.map { oldMap[it]!! },
        changed.map { nowMap[it]!! }
    )
}

data class RegistryDiff<T : Any>(
    val added: List<Holder<T>>,
    val removed: List<Holder<T>>,
    val changedIds: List<Holder<T>>,
)

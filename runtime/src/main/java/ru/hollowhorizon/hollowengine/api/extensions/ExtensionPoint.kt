package ru.hollowhorizon.hollowengine.api.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtension
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtensionChange
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonRegistration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * Named place the engine reads contributions from, and addons write them to.
 * Entries are ordered by priority and then by registration order.
 */
class ExtensionPoint<T : Any> internal constructor(
    val id: ResourceLocation,
    private val extensionType: KClass<T>? = null,
) : Iterable<T> {
    @Volatile
    private var entries: List<HollowAddonExtension<T>> = emptyList()

    @Volatile
    private var listeners: List<(HollowAddonExtensionChange<T>) -> Unit> = emptyList()

    @Volatile
    private var valuesSnapshot: List<T> = emptyList()

    private var nextOrder = 0L

    @Volatile
    var generation: Int = 0
        private set

    val revision: Long get() = generation.toLong()
    val extensions: List<T> get() = valuesSnapshot

    override fun iterator(): Iterator<T> = valuesSnapshot.iterator()

    fun extensions(): List<HollowAddonExtension<T>> = entries
    fun values(): List<T> = valuesSnapshot

    /**
     * Engine registrations keep their historical replace-by-key behavior.
     * A stale handle cannot remove a newer registration with the same key.
     */
    @Synchronized
    fun register(key: ResourceLocation, extension: T): ExtensionHandle {
        validateType(extension)
        val previous = entries.firstOrNull { it.qualifiedId == key.toString() }
        if (previous != null) remove(previous)
        val entry = entry(
            key, key.namespace, key.path, extension, 0,
            extension.javaClass.classLoader ?: ExtensionPoint::class.java.classLoader,
        )
        add(entry)
        return ExtensionHandle { remove(entry) }
    }

    @Synchronized
    fun unregister(key: ResourceLocation): Boolean {
        val entry = entries.firstOrNull { it.qualifiedId == key.toString() } ?: return false
        return remove(entry)
    }

    fun find(key: ResourceLocation): T? = entries.firstOrNull { it.qualifiedId == key.toString() }?.value

    /** Addon registrations have owner-qualified IDs and reject duplicate IDs. */
    @Synchronized
    internal fun register(
        key: ResourceLocation,
        ownerId: String,
        localId: String,
        classLoader: ClassLoader,
        priority: Int,
        extension: T,
    ): HollowAddonRegistration {
        validateType(extension)
        require(entries.none { it.qualifiedId == key.toString() }) {
            "Extension '$key' is already registered in '$id'"
        }
        val entry = entry(key, ownerId, localId, extension, priority, classLoader)
        add(entry)
        return PointRegistration { remove(entry) }
    }

    @Synchronized
    fun observe(listener: (HollowAddonExtensionChange<T>) -> Unit): HollowAddonRegistration {
        listeners = listeners + listener
        return PointRegistration { synchronized(this) { listeners = listeners - listener } }
    }

    fun onChange(listener: () -> Unit): ExtensionHandle {
        val registration = observe { listener() }
        return ExtensionHandle { registration.close() }
    }

    private fun validateType(extension: T) {
        require(extensionType == null || extensionType.java.isInstance(extension)) {
            "Extension ${extension::class.qualifiedName} is not an instance of ${extensionType?.qualifiedName}"
        }
    }

    private fun entry(
        key: ResourceLocation,
        ownerId: String,
        localId: String,
        extension: T,
        priority: Int,
        classLoader: ClassLoader,
    ) = HollowAddonExtension(
        ownerId = ownerId,
        localId = localId,
        qualifiedId = key.toString(),
        priority = priority,
        value = extension,
        classLoader = classLoader,
        order = nextOrder++,
    )

    private fun add(entry: HollowAddonExtension<T>) {
        entries = (entries + entry).sortedWith(
            compareByDescending<HollowAddonExtension<T>> { it.priority }.thenBy { it.order },
        )
        valuesSnapshot = entries.map { it.value }
        generation++
        notifyListeners(HollowAddonExtensionChange.Added(entry))
    }

    @Synchronized
    private fun remove(entry: HollowAddonExtension<T>): Boolean {
        if (entries.none { it === entry }) return false
        entries = entries.filterNot { it === entry }
        valuesSnapshot = entries.map { it.value }
        generation++
        notifyListeners(HollowAddonExtensionChange.Removed(entry))
        return true
    }

    private fun notifyListeners(change: HollowAddonExtensionChange<T>) {
        listeners.forEach { listener ->
            runCatching { listener(change) }.onFailure {
                HollowEngine.LOGGER.error("Extension point '{}' listener failed", id, it)
            }
        }
    }

    private class PointRegistration(private val cleanup: () -> Unit) : HollowAddonRegistration {
        private val active = AtomicBoolean(true)
        override val isActive: Boolean get() = active.get()
        override fun close() {
            if (active.compareAndSet(true, false)) cleanup()
        }
    }
}

fun interface ExtensionHandle : AutoCloseable {
    override fun close()
}

fun ExtensionHandle.closeWith(scope: CoroutineScope): ExtensionHandle {
    scope.coroutineContext.job.invokeOnCompletion { close() }
    return this
}

object ExtensionPoints {
    private val points = HashMap<ResourceLocation, ExtensionPoint<*>>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Any> create(id: ResourceLocation, type: KClass<T>? = null): ExtensionPoint<T> =
        points.getOrPut(id) { ExtensionPoint(id, type) } as ExtensionPoint<T>
}

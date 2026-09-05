package ru.hollowhorizon.hollowengine.common.addons

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/** A reversible contribution owned by an addon or by the host. */
interface HollowAddonRegistration : AutoCloseable {
    val isActive: Boolean

    override fun close()
}

/**
 * One typed extension point exposed by HollowEngine.
 *
 * Contributions are ordered by priority and then by registration order. Their qualified identity is
 * always `<owner-id>:<local-id>`, so two addons may use the same local name without colliding.
 */
class HollowAddonExtensionPoint<T : Any>(
    val id: String,
    private val extensionType: KClass<T>,
) {
    private val entries = CopyOnWriteArrayList<HollowAddonExtension<T>>()
    private val listeners = CopyOnWriteArrayList<(HollowAddonExtensionChange<T>) -> Unit>()
    private val nextOrder = AtomicLong()
    private val revisionCounter = AtomicLong()

    val revision: Long
        get() = revisionCounter.get()

    init {
        require(id.isNotBlank()) { "Extension point ID cannot be blank" }
    }

    fun extensions(): List<HollowAddonExtension<T>> = entries
        .sortedWith(compareByDescending<HollowAddonExtension<T>> { it.priority }.thenBy { it.order })

    fun values(): List<T> = extensions().map(HollowAddonExtension<T>::value)

    /** Observes changes without retaining an addon scope. Intended for the subsystem owning this point. */
    fun observe(listener: (HollowAddonExtensionChange<T>) -> Unit): HollowAddonRegistration {
        listeners += listener
        return CallbackRegistration { listeners -= listener }
    }

    internal fun register(
        ownerId: String,
        localId: String,
        classLoader: ClassLoader,
        priority: Int,
        extension: T,
    ): HollowAddonRegistration {
        require(extensionType.java.isInstance(extension)) {
            "Extension ${extension::class.qualifiedName} is not an instance of ${extensionType.qualifiedName}"
        }
        val qualifiedId = qualifyExtensionId(ownerId, localId)
        val entry = HollowAddonExtension(
            ownerId = ownerId,
            localId = localId,
            qualifiedId = qualifiedId,
            priority = priority,
            value = extension,
            classLoader = classLoader,
            order = nextOrder.getAndIncrement(),
        )
        synchronized(entries) {
            require(entries.none { it.qualifiedId == qualifiedId }) {
                "Extension '$qualifiedId' is already registered in '$id'"
            }
            entries += entry
        }
        revisionCounter.incrementAndGet()
        notifyListeners(HollowAddonExtensionChange.Added(entry))
        return CallbackRegistration {
            val removed = synchronized(entries) { entries.remove(entry) }
            if (removed) {
                revisionCounter.incrementAndGet()
                notifyListeners(HollowAddonExtensionChange.Removed(entry))
            }
        }
    }

    private fun notifyListeners(change: HollowAddonExtensionChange<T>) {
        listeners.forEach { listener ->
            runCatching { listener(change) }
                .onFailure { failure ->
                    HollowEngine.LOGGER.error(
                        "Extension point '{}' listener failed while processing '{}'",
                        id,
                        change.extension.qualifiedId,
                        failure,
                    )
                }
        }
    }
}

/** Metadata retained by the host for an installed extension. */
class HollowAddonExtension<T : Any> internal constructor(
    val ownerId: String,
    val localId: String,
    val qualifiedId: String,
    val priority: Int,
    val value: T,
    internal val classLoader: ClassLoader,
    internal val order: Long,
) {
    /** Runs an extension callback with the classloader that defined it as the thread context loader. */
    fun <R> invoke(block: (T) -> R): R = withHollowAddonClassLoader(classLoader) { block(value) }
}

sealed interface HollowAddonExtensionChange<T : Any> {
    val extension: HollowAddonExtension<T>

    data class Added<T : Any>(override val extension: HollowAddonExtension<T>) : HollowAddonExtensionChange<T>

    data class Removed<T : Any>(override val extension: HollowAddonExtension<T>) : HollowAddonExtensionChange<T>
}

/** Owner-bound registration facade available to an addon through [HollowAddonContext.extensions]. */
interface HollowAddonExtensions {
    val addonId: String

    fun qualify(localId: String): String = qualifyExtensionId(addonId, localId)

    fun <T : Any> register(
        point: HollowAddonExtensionPoint<T>,
        id: String,
        extension: T,
        priority: Int = 0,
    ): HollowAddonRegistration

    /** Registers arbitrary deterministic cleanup for resources which do not have an extension point. */
    fun onUnload(cleanup: () -> Unit): HollowAddonRegistration
}

/** Source-compatible accessor which does not alter the binary constructor of [HollowAddonContext]. */
val HollowAddonContext.extensions: HollowAddonExtensions
    get() = koin.get()

internal class OwnedHollowAddonExtensions(
    override val addonId: String,
    private val classLoader: ClassLoader,
) : HollowAddonExtensions {
    private val registrationLock = Any()
    private val registrations = mutableListOf<HollowAddonRegistration>()
    private val closed = AtomicBoolean()

    override fun <T : Any> register(
        point: HollowAddonExtensionPoint<T>,
        id: String,
        extension: T,
        priority: Int,
    ): HollowAddonRegistration {
        check(!closed.get()) { "Addon extension scope '$addonId' is already closed" }
        return own(point.register(addonId, id, classLoader, priority, extension))
    }

    override fun onUnload(cleanup: () -> Unit): HollowAddonRegistration {
        check(!closed.get()) { "Addon extension scope '$addonId' is already closed" }
        return own(CallbackRegistration { withHollowAddonClassLoader(classLoader, cleanup) })
    }

    fun cleanup() {
        val owned = synchronized(registrationLock) {
            if (!closed.compareAndSet(false, true)) return
            registrations.asReversed().toList().also { registrations.clear() }
        }
        owned.forEach { registration ->
            runCatching { registration.close() }
                .onFailure { failure ->
                    HollowEngine.LOGGER.error("Failed to remove an extension owned by addon '{}'", addonId, failure)
                }
        }
    }

    private fun own(registration: HollowAddonRegistration): HollowAddonRegistration {
        val accepted = synchronized(registrationLock) {
            if (closed.get()) false else {
                registrations += registration
                true
            }
        }
        if (!accepted) {
            registration.close()
            error("Addon extension scope '$addonId' was closed during registration")
        }
        return registration
    }
}

internal class HostHollowAddonExtensions(
    ownerId: String,
    classLoader: ClassLoader,
) : HollowAddonExtensions by OwnedHollowAddonExtensions(ownerId, classLoader)

private class CallbackRegistration(
    private val cleanup: () -> Unit,
) : HollowAddonRegistration {
    private val active = AtomicBoolean(true)

    override val isActive: Boolean
        get() = active.get()

    override fun close() {
        if (active.compareAndSet(true, false)) cleanup()
    }
}

private fun qualifyExtensionId(ownerId: String, localId: String): String {
    require(ownerId.isNotBlank()) { "Extension owner ID cannot be blank" }
    require(localId.isNotBlank()) { "Extension ID cannot be blank" }
    val qualified = if (':' in localId) localId else "$ownerId:$localId"
    require(qualified.substringBefore(':') == ownerId) {
        "Extension '$localId' must belong to addon '$ownerId'"
    }
    requireNotNull(ResourceLocation.tryParse(qualified)) { "Invalid extension ID '$qualified'" }
    return qualified
}

internal inline fun <R> withHollowAddonClassLoader(classLoader: ClassLoader, block: () -> R): R {
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    thread.contextClassLoader = classLoader
    return try {
        block()
    } finally {
        thread.contextClassLoader = previous
    }
}

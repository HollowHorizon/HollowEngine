package ru.hollowhorizon.hollowengine.common.addons

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionPoint
import java.util.concurrent.atomic.AtomicBoolean

/** A reversible contribution owned by an addon or by the host. */
interface HollowAddonRegistration : AutoCloseable {
    val isActive: Boolean

    override fun close()
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
        point: ExtensionPoint<T>,
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
        point: ExtensionPoint<T>,
        id: String,
        extension: T,
        priority: Int,
    ): HollowAddonRegistration {
        check(!closed.get()) { "Addon extension scope '$addonId' is already closed" }
        val key = ResourceLocation.parse(qualifyExtensionId(addonId, id))
        return own(point.register(key, addonId, id, classLoader, priority, extension))
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

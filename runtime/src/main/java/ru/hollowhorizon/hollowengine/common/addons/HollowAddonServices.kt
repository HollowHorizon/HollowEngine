package ru.hollowhorizon.hollowengine.common.addons

import ru.hollowhorizon.hollowengine.HollowEngine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

internal class HollowAddonServiceRegistry : HollowAddonHostServices {
    private val services = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<Any>>()

    override fun log(message: String) {
        HollowEngine.LOGGER.info("[addons] {}", message)
    }

    override fun <T : Any> publishService(type: KClass<T>, instance: T) {
        require(type.java.isInstance(instance)) {
            "Service ${instance::class.qualifiedName} is not an instance of ${type.qualifiedName}"
        }
        services.computeIfAbsent(type) { CopyOnWriteArrayList() }.addIfAbsent(instance)
    }

    override fun <T : Any> unpublishService(type: KClass<T>, instance: T) {
        services[type]?.let { registered ->
            registered.removeIf { it === instance }
            if (registered.isEmpty()) services.remove(type, registered)
        }
    }

    override fun <T : Any> findService(type: KClass<T>): T? = findServices(type).lastOrNull()

    override fun <T : Any> findServices(type: KClass<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return services[type]?.toList() as? List<T> ?: emptyList()
    }

    fun ownedBy(addonId: String): OwnedHollowAddonHostServices = OwnedHollowAddonHostServices(this, addonId)
}

internal class OwnedHollowAddonHostServices(
    private val delegate: HollowAddonServiceRegistry,
    private val addonId: String,
) : HollowAddonHostServices by delegate {
    private val registrations = CopyOnWriteArrayList<ServiceRegistration>()

    override fun log(message: String) {
        HollowEngine.LOGGER.info("[addon:{}] {}", addonId, message)
    }

    override fun <T : Any> publishService(type: KClass<T>, instance: T) {
        delegate.publishService(type, instance)
        registrations.add(ServiceRegistration(type, instance))
    }

    override fun <T : Any> unpublishService(type: KClass<T>, instance: T) {
        delegate.unpublishService(type, instance)
        registrations.removeIf { it.type == type && it.instance === instance }
    }

    fun cleanup() {
        registrations.toList().asReversed().forEach { registration ->
            @Suppress("UNCHECKED_CAST")
            delegate.unpublishService(registration.type as KClass<Any>, registration.instance)
        }
        registrations.clear()
    }

    private data class ServiceRegistration(
        val type: KClass<*>,
        val instance: Any,
    )
}

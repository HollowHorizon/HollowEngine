package ru.hollowhorizon.hollowengine.common.geary.components

import com.mineinabyss.geary.datatypes.Component
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryState
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

enum class ComponentPersistencePolicy {
    PERSIST,
    LOOSE_ON_DEATH,
}

enum class ComponentSyncPolicy {
    NONE,
    SYNC,
}

data class ComponentDescriptor<T : Any>(
    val id: ResourceLocation,
    val value: KClass<T>,
    val serializer: KSerializer<T>,
    val editable: Boolean = true,
    val persistencePolicy: ComponentPersistencePolicy = ComponentPersistencePolicy.PERSIST,
    val syncPolicy: ComponentSyncPolicy = ComponentSyncPolicy.NONE,
    val defaultFactory: (() -> T)? = null,
) {
    fun create(): T = defaultFactory?.invoke() ?: value.createInstance()
}

object ComponentDescriptorRegistry :
    MutableRegistry<ComponentDescriptor<*>> by RegistryManager.create("hollowengine:component_descriptors".rl) {
    private val mutableRegistry: MutableRegistry<ComponentDescriptor<*>>
        get() = this as MutableRegistry<ComponentDescriptor<*>>

    private fun ensureRegisteringState() {
        when (state) {
            RegistryState.FROZEN -> {
                mutableRegistry.unfreeze()
                mutableRegistry.unbake()
            }
            RegistryState.BAKED -> mutableRegistry.unbake()
            RegistryState.CONSTRUCTING,
            RegistryState.REGISTERING,
            -> Unit
        }
    }

    fun register(descriptor: ComponentDescriptor<*>): ComponentDescriptor<*> {
        ensureRegisteringState()
        mutableRegistry.register(descriptor.id) { descriptor }
        mutableRegistry.bake()
        return descriptor
    }

    fun unregisterDescriptor(id: ResourceLocation): Boolean {
        ensureRegisteringState()
        val removed = mutableRegistry.unregister(id)
        mutableRegistry.bake()
        return removed
    }

    fun descriptorOrNull(id: ResourceLocation): ComponentDescriptor<*>? = getOrNull(id)

    fun descriptorOrNull(type: KClass<*>): ComponentDescriptor<*>? =
        firstOrNull { it.value.value == type }?.value

    fun idFor(type: KClass<*>): ResourceLocation? = descriptorOrNull(type)?.id

    fun serializersModule(): SerializersModule = SerializersModule {
        polymorphic(Component::class) {
            ComponentDescriptorRegistry.map { it.value }.forEach { descriptor ->
                @Suppress("UNCHECKED_CAST")
                subclass(
                    descriptor.value as KClass<Component>,
                    descriptor.serializer as KSerializer<Component>
                )
            }
        }
    }
}

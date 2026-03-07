package ru.hollowhorizon.hollowengine.common.geary.components

import net.minecraft.resources.ResourceLocation

@Deprecated("Use ComponentDescriptorRegistry", ReplaceWith("ComponentDescriptorRegistry"))
object ComponentRegistry {
    fun register(path: ResourceLocation, component: ComponentDescriptor<*>): ComponentDescriptor<*> =
        ComponentDescriptorRegistry.register(component.copy(id = path))

    operator fun get(path: ResourceLocation): ComponentDescriptor<*> = ComponentDescriptorRegistry[path]
    fun getOrNull(path: ResourceLocation): ComponentDescriptor<*>? = ComponentDescriptorRegistry.getOrNull(path)
    fun unregister(path: ResourceLocation): Boolean = ComponentDescriptorRegistry.unregisterDescriptor(path)

    val keys: Set<ResourceLocation>
        get() = ComponentDescriptorRegistry.map { it.key }.toSet()

    fun asSequence(): Sequence<ru.hollowhorizon.hollowengine.common.registry.system.Holder<ComponentDescriptor<*>>> =
        ComponentDescriptorRegistry.asSequence()

    fun forEach(action: (ru.hollowhorizon.hollowengine.common.registry.system.Holder<ComponentDescriptor<*>>) -> Unit) {
        ComponentDescriptorRegistry.forEach(action)
    }

    fun <R> map(transform: (ru.hollowhorizon.hollowengine.common.registry.system.Holder<ComponentDescriptor<*>>) -> R): List<R> =
        ComponentDescriptorRegistry.map(transform)
}

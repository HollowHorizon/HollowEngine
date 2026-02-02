package ru.hollowhorizon.hollowengine.common.geary.components

import net.minecraft.resources.ResourceLocation

object ComponentRegistry : Collection<ComponentHolder<*>> {
    private val components = hashMapOf<ResourceLocation, ComponentHolder<*>>()

    fun register(path: ResourceLocation, component: ComponentHolder<*>) {
        components[path] = component
    }

    operator fun get(path: ResourceLocation) = components[path]

    val keys get() = components.keys
    override val size: Int get() = components.size
    override fun isEmpty() = components.isEmpty()
    override fun contains(element: ComponentHolder<*>) = element in components.values
    override fun iterator(): Iterator<ComponentHolder<*>> = components.values.iterator()
    override fun containsAll(elements: Collection<ComponentHolder<*>>) = components.values.containsAll(elements)
}
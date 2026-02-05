package ru.hollowhorizon.hollowengine.common.geary.components

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.rl

object ComponentRegistry : MutableRegistry<ComponentHolder<*>> by RegistryManager.create("hollowengine:geary_components".rl) {
    fun register(path: ResourceLocation, component: ComponentHolder<*>) {
        (this as MutableRegistry<ComponentHolder<*>>).register(path) { component }
    }

    val keys: Set<ResourceLocation> get() = this.map { it.key }.toSet()
}

package ru.hollowhorizon.hollowengine.common.components.registry

import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.rl

val ComponentRegistry = RegistryManager.create<ComponentEntry>("hollowengine:components".rl)

data class ComponentEntry(
    val generator: () -> Component<*>,
    val type: Class<*>
): () -> Component<*> {
    override fun invoke() = generator()
}
package ru.hollowhorizon.hollowengine.common.components.registry

import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.entity.ModelComponent
import ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks.forceCast
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.KClass

object ComponentRegistry : MutableRegistry<ComponentEntry<*>> by RegistryManager.create("hollowengine:components".rl)

@Init
object ModComponents {
    val MODEL_COMPONENT by ComponentRegistry.register("hollowengine:model_component".rl) {
        ComponentEntry.create(::ModelComponent)
    }
}

data class ComponentEntry<T : Any>(
    val targetType: KClass<T>,
    val generator: (T) -> Component<T>,
) : (T) -> Component<T> {
    override fun invoke(value: T) = generator(value)

    companion object {
        inline fun <reified T : Any> create(noinline generator: (T) -> Component<T>): ComponentEntry<T> =
            ComponentEntry(T::class, generator)
    }
}

fun ComponentEntry<*>.create(any: Any): Component<*> = this(forceCast(any))
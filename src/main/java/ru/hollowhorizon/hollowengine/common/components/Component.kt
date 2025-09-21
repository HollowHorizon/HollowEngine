package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.property.Property
import ru.hollowhorizon.hollowengine.common.components.property.Sync
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

abstract class Component<T : Any> {
    lateinit var owner: T
    val isClient get() = (owner as? Entity)?.level()?.isClientSide == true
    val properties: MutableMap<String, Property<*>> = mutableMapOf()
    val dependencies: MutableList<KClass<out Component<*>>> = mutableListOf()
    var enabled by property(enabledKey) { true }
        .sync(Sync.ON_CHANGE)
        .copyOnDeath()

    inline fun <reified V : Any> property(name: String? = null, noinline initializer: () -> V) = Property(name, V::class.java, initializer)


    inline fun <reified C : Component<*>> component(): C {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("No ComponentMeta annotation found for ${C::class.simpleName}")
        val component = (owner as ComponentDispatcher).`hollowcore$components`.getOrPut(location) {
            ComponentRegistry[keyOf(location)]()
        } as C
        dependencies.add(component::class)
        return component
    }

    inline fun <reified C : Component<*>> requires(): C {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("No ComponentMeta annotation found for ${C::class.simpleName}")
        val component = (owner as ComponentDispatcher).`hollowcore$components`[location] as? C
            ?: throw IllegalStateException("Required component ${C::class.simpleName} not found")
        dependencies.add(component::class)
        return component
    }

    open fun onAttach() {}
    open fun onDetach() {}
    open fun onTick() {}
    open fun onEnabled() {}
    open fun onDisabled() {}

    fun enable() {
        if (!enabled) {
            enabled = true
            onEnabled()
        }
    }

    fun disable() {
        if (enabled) {
            enabled = false
            onDisabled()
        }
    }

    companion object {
        val enabledKey = "#internal:enabled"
    }
}
package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.property.Property
import ru.hollowhorizon.hollowengine.common.components.property.Sync
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

abstract class Component<T : Any> {
    lateinit var owner: T
    val properties: MutableMap<String, Property<*>> = mutableMapOf()
    var enabled by property(ENABLED_KEY) { true }
        .sync(Sync.ON_CHANGE)
        .copyOnDeath()

    inline fun <reified V : Any> property(name: String? = null, noinline initializer: () -> V) =
        Property(this, name, V::class.java, initializer)

    inline fun <reified C : Component<*>> requires(): Lazy<C> = lazy {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("No ComponentMeta annotation found for ${C::class.simpleName}")
        val component = (owner as ComponentDispatcher).`hollowcore$components`[location] as? C
            ?: throw IllegalStateException("Required component ${C::class.simpleName} not found")
        component
    }

    open fun onAttach() {}
    open fun onDetach() {}
    open fun onTick() {}
    open fun onEnabled() {}
    open fun onDisabled() {}

    open fun saveExtras(tag: CompoundTag) {}
    open fun loadExtras(tag: CompoundTag) {}

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
        val ENABLED_KEY = "#internal:enabled"
    }
}
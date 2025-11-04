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

    private var onAttaches = HashSet<() -> Unit>()
    private var onDetachs = HashSet<() -> Unit>()
    private var onTicks = HashSet<() -> Unit>()
    private var onEnableds = HashSet<() -> Unit>()
    private var onDisableds = HashSet<() -> Unit>()
    private var onSaves = HashSet<CompoundTag.() -> Unit>()
    private var onLoads = HashSet<CompoundTag.() -> Unit>()

    fun onAttach() {
        onAttaches.forEach { it() }
    }

    fun onDetach() {
        onDetachs.forEach { it() }
    }

    fun onTick() {
        onTicks.forEach { it() }
    }

    fun onEnabled() {
        onEnableds.forEach { it() }
    }

    fun onDisabled() {
        onDisableds.forEach { it() }
    }

    fun saveExtras(tag: CompoundTag) {
        onSaves.forEach { it(tag) }
    }

    fun loadExtras(tag: CompoundTag) {
        onLoads.forEach { it(tag) }
    }

    fun onAttach(action: () -> Unit) {
        onAttaches.add(action)
    }

    fun onDetach(action: () -> Unit) {
        onDetachs.add(action)
    }

    fun onUpdate(action: () -> Unit) {
        onTicks.add(action)
    }

    fun onEnabled(action: () -> Unit) {
        onEnableds.add(action)
    }

    fun onDisabled(action: () -> Unit) {
        onDisableds.add(action)
    }

    fun onSave(action: CompoundTag.() -> Unit) {
        onSaves.add(action)
    }

    fun onLoad(action: CompoundTag.() -> Unit) {
        onLoads.add(action)
    }

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
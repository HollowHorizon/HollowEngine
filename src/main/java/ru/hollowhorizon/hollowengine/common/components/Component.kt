package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.components.property.Property
import ru.hollowhorizon.hollowengine.common.components.property.Sync

abstract class Component<T : Any>(val owner: T) {
    val properties: MutableMap<String, Property<*>> = mutableMapOf()
    var enabled by property(ENABLED_KEY) { true }
        .sync(Sync.ON_CHANGE)
        .copyOnDeath()

    inline fun <reified V : Any> property(name: String? = null, noinline initializer: () -> V) =
        Property(this, name, V::class.java, initializer)

    var changedProperties = mutableSetOf<String>()

    private var onAttaches = HashSet<() -> Unit>()
    private var onDetachs = HashSet<() -> Unit>()
    private var onTicks = HashSet<() -> Unit>()
    private var onEnableds = HashSet<() -> Unit>()
    private var onDisableds = HashSet<() -> Unit>()

    internal open fun onAttach() {
        onAttaches.forEach { it() }
    }

    internal open fun onDetach() {
        onDetachs.forEach { it() }
    }

    internal open fun onTick() {
        onTicks.forEach { it() }
    }

    internal open fun onEnabled() {
        onEnableds.forEach { it() }
    }

    internal open fun onDisabled() {
        onDisableds.forEach { it() }
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

    open fun serialize(tag: CompoundTag) {

    }

    open fun deserialize(tag: CompoundTag) {

    }

    companion object {
        val ENABLED_KEY = "#internal:enabled"
    }
}
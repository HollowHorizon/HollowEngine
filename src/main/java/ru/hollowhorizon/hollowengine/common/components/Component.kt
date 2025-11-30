package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.components.events.ComponentEventSubscriber
import ru.hollowhorizon.hollowengine.common.components.property.Property
import ru.hollowhorizon.hollowengine.common.components.property.Sync

abstract class Component<T : Any>(val owner: T) {
    val properties: MutableMap<String, Property<*>> = mutableMapOf()
    var enabled by property(ENABLED_KEY) { true }
        .sync(Sync.ON_CHANGE)
        .copyOnDeath()

    inline fun <reified V : Any> property(name: String? = null, noinline initializer: () -> V) =
        Property(this, name, V::class.java, initializer)

    private var onAttaches = HashSet<() -> Unit>()
    private var onDetachs = HashSet<() -> Unit>()
    private var onTicks = HashSet<() -> Unit>()
    private var onEnableds = HashSet<() -> Unit>()
    private var onDisableds = HashSet<() -> Unit>()
    private var onSaves = HashSet<CompoundTag.() -> Unit>()
    private var onLoads = HashSet<CompoundTag.() -> Unit>()

    init {
        with(ComponentEventSubscriber) { setupEvents() }
    }

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

    internal open fun saveExtras(tag: CompoundTag) {
        onSaves.forEach { it(tag) }
    }

    internal open fun loadExtras(tag: CompoundTag) {
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
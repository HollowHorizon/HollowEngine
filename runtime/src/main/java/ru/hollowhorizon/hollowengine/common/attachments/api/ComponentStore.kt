package ru.hollowhorizon.hollowengine.common.attachments.api

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.attachments.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySnapshot

/**
 * The components attached to one entity.
 */
class ComponentStore internal constructor() {
    private val components = LinkedHashMap<ResourceLocation, Component>()

    @Volatile
    private var cachedSnapshot: EntitySnapshot? = null

    /**
     * Called after every mutation, whichever path it came through. This is the single choke point the
     * network layer hangs off, so a component write can never be made without the sync noticing.
     */
    var onChange: (() -> Unit)? = null

    val isEmpty: Boolean get() = components.isEmpty()

    /** Read-only view for callers that only look things up; never invalidates the snapshot cache. */
    val readOnly: Map<ResourceLocation, Component> get() = components

    /** Mutable view. Every mutating path drops the cached snapshot. */
    fun asMutableMap(): MutableMap<ResourceLocation, Component> = View()

    fun snapshot(entity: Entity): EntitySnapshot? {
        if (components.isEmpty()) return null
        cachedSnapshot?.let { if (it.entity === entity) return it }
        return EntitySnapshot(components = components.values.toList())
            .withEntity(entity)
            .also { cachedSnapshot = it }
    }

    /** Puts [component] under the id its descriptor gives it, replacing whatever was there. */
    fun put(component: Component) {
        components[idOf(component)] = component
        invalidate()
    }

    fun replaceAll(source: Collection<Component>) {
        components.clear()
        source.forEach { component -> components[idOf(component)] = component }
        invalidate()
    }

    fun copyOf(): Map<ResourceLocation, Component> = LinkedHashMap(components)

    fun putAll(source: Map<ResourceLocation, Component>) {
        components.putAll(source)
        invalidate()
    }

    fun clear() {
        components.clear()
        invalidate()
    }

    private fun invalidate() {
        cachedSnapshot = null
        onChange?.invoke()
    }

    private inner class View : AbstractMutableMap<ResourceLocation, Component>() {
        override val entries: MutableSet<MutableMap.MutableEntry<ResourceLocation, Component>>
            get() = EntrySet()

        override fun put(key: ResourceLocation, value: Component): Component? =
            components.put(key, value).also { invalidate() }

        override fun remove(key: ResourceLocation): Component? {
            val previous = components.remove(key) ?: return null
            invalidate()
            return previous
        }

        override fun clear() {
            components.clear()
            invalidate()
        }

        override fun containsKey(key: ResourceLocation): Boolean = components.containsKey(key)

        override fun get(key: ResourceLocation): Component? = components[key]

        override val size: Int get() = components.size
    }

    /**
     * Backed by the real entry set rather than a copy, so `keys.removeIf { .. }` and `setValue` reach
     * the underlying map and invalidate the cache when they do.
     */
    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<ResourceLocation, Component>>() {
        override val size: Int get() = components.size

        override fun add(element: MutableMap.MutableEntry<ResourceLocation, Component>): Boolean {
            val previous = components.put(element.key, element.value)
            invalidate()
            return previous != element.value
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<ResourceLocation, Component>> {
            val delegate = components.entries.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<ResourceLocation, Component>> {
                override fun hasNext(): Boolean = delegate.hasNext()

                override fun next(): MutableMap.MutableEntry<ResourceLocation, Component> {
                    val entry = delegate.next()
                    return object : MutableMap.MutableEntry<ResourceLocation, Component> {
                        override val key: ResourceLocation get() = entry.key
                        override val value: Component get() = entry.value
                        override fun setValue(newValue: Component): Component =
                            entry.setValue(newValue).also { invalidate() }
                    }
                }

                override fun remove() {
                    delegate.remove()
                    invalidate()
                }
            }
        }
    }

    private fun idOf(component: Component): ResourceLocation =
        ComponentDescriptorRegistry.idFor(component::class)
            ?: error("Component descriptor not found for ${component::class.qualifiedName}")
}

/**
 * Drops the components whose descriptor marks them as lost on death. Takes a plain map rather than a
 * store, because a respawn also has to filter the set cached for a player that is already removed.
 */
fun Map<ResourceLocation, Component>.withoutLooseOnDeath(): Map<ResourceLocation, Component> =
    filterNot { (_, component) -> ComponentDescriptorRegistry.isLooseOnDeath(component) }

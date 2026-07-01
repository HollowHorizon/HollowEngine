package ru.hollowhorizon.hollowengine.client.ui

import java.util.*

private const val MaxNodeMeasureCacheEntries = 64

internal class InvalidatingMutableList<E>(
    values: Iterable<E> = emptyList(),
    private val onChange: () -> Unit,
) : AbstractMutableList<E>() {
    private val items = values.toMutableList()

    override val size: Int
        get() = items.size

    override fun get(index: Int): E = items[index]

    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        onChange()
    }

    override fun add(index: Int, element: E) {
        items.add(index, element)
        onChange()
    }

    override fun removeAt(index: Int): E {
        return items.removeAt(index).also { onChange() }
    }

    override fun set(index: Int, element: E): E {
        val previous = items.set(index, element)
        if (previous != element) onChange()
        return previous
    }
}

internal class InvalidatingMutableSet<E>(
    values: Iterable<E> = emptyList(),
    private val onChange: () -> Unit,
) : AbstractMutableSet<E>() {
    private val items = values.toMutableSet()

    override val size: Int
        get() = items.size

    override fun contains(element: E): Boolean = items.contains(element)

    override fun add(element: E): Boolean {
        return items.add(element).also { changed -> if (changed) onChange() }
    }

    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        onChange()
    }

    override fun iterator(): MutableIterator<E> {
        val iterator = items.iterator()
        return object : MutableIterator<E> {
            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): E = iterator.next()

            override fun remove() {
                iterator.remove()
                onChange()
            }
        }
    }
}

internal class InvalidatingMutableMap<K, V>(
    values: Map<K, V> = emptyMap(),
    private val onChange: () -> Unit,
) : AbstractMutableMap<K, V>() {
    private val items = values.toMutableMap()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = InvalidatingEntrySet(items.entries, onChange)

    override fun containsKey(key: K): Boolean = items.containsKey(key)

    override fun containsValue(value: V): Boolean = items.containsValue(value)

    override fun get(key: K): V? = items[key]

    override fun put(key: K, value: V): V? {
        val hadKey = items.containsKey(key)
        val previous = items.put(key, value)
        if (!hadKey || previous != value) onChange()
        return previous
    }

    override fun remove(key: K): V? {
        if (!items.containsKey(key)) return null
        return items.remove(key).also { onChange() }
    }

    override fun clear() {
        if (items.isEmpty()) return
        items.clear()
        onChange()
    }

    private class InvalidatingEntrySet<K, V>(
        private val entries: MutableSet<MutableMap.MutableEntry<K, V>>,
        private val onChange: () -> Unit,
    ) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int
            get() = entries.size

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            return entries.add(element).also { changed -> if (changed) onChange() }
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            val iterator = entries.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                override fun hasNext(): Boolean = iterator.hasNext()

                override fun next(): MutableMap.MutableEntry<K, V> {
                    val entry = iterator.next()
                    return InvalidatingEntry(entry, onChange)
                }

                override fun remove() {
                    iterator.remove()
                    onChange()
                }
            }
        }
    }

    private class InvalidatingEntry<K, V>(
        private val entry: MutableMap.MutableEntry<K, V>,
        private val onChange: () -> Unit,
    ) : MutableMap.MutableEntry<K, V> {
        override val key: K
            get() = entry.key

        override val value: V
            get() = entry.value

        override fun setValue(newValue: V): V {
            val previous = entry.setValue(newValue)
            if (previous != newValue) onChange()
            return previous
        }
    }
}

class UiNodeLayoutState internal constructor(private val owner: UiNode) {
    private var parent: UiNode? = null
    private var childrenSignature = 0
    private var resolvedLayoutFingerprint = 0
    private var revision = UiLayoutRevision.next()

    val subtreeRevision: Long
        get() = revision

    internal val parentNode: UiNode?
        get() = parent

    internal fun attachTo(nextParent: UiNode?) {
        if (parent === nextParent) return
        parent = nextParent
        invalidate()
    }

    internal fun synchronizeChildren() {
        var signature = owner.children.size
        owner.children.forEach { child ->
            child.layoutState.attachTo(owner)
            signature = 31 * signature + System.identityHashCode(child)
        }
        if (childrenSignature == signature) return
        childrenSignature = signature
        invalidate()
    }

    internal fun updateResolvedLayoutFingerprint(fingerprint: Int) {
        if (resolvedLayoutFingerprint == fingerprint) return
        resolvedLayoutFingerprint = fingerprint
        invalidate()
    }

    internal fun invalidate() {
        val nextRevision = UiLayoutRevision.next()
        markSubtreeChanged(nextRevision)
    }

    private fun markSubtreeChanged(nextRevision: Long) {
        if (revision == nextRevision) return
        revision = nextRevision
        parent?.layoutState?.markSubtreeChanged(nextRevision)
    }
}

internal fun UiNode.invalidateLayout() {
    layoutState.invalidate()
}

internal fun UiNode.detachLayoutParentRecursively() {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        node.layoutState.attachTo(null)
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
}

private object UiLayoutRevision {
    private var value = 0L

    fun next(): Long {
        value += 1L
        return value
    }
}

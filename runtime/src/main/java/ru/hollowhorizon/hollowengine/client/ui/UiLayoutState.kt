package ru.hollowhorizon.hollowengine.client.ui

import java.util.LinkedHashMap

private const val MaxNodeMeasureCacheEntries = 64

class UiNodeLayoutState internal constructor(private val owner: UiNode) {
    private val measureCache = object : LinkedHashMap<Any, LayoutSize>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, LayoutSize>): Boolean {
            return size > MaxNodeMeasureCacheEntries
        }
    }

    private var parent: UiNode? = null
    private var childrenSignature = 0
    private var resolvedLayoutFingerprint = 0
    private var revision = UiLayoutRevision.next()

    val subtreeRevision: Long
        get() = revision

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

    internal fun cachedMeasure(key: Any, producer: () -> LayoutSize): LayoutSize {
        measureCache[key]?.let { return it }
        return producer().also { measureCache[key] = it }
    }

    internal fun invalidate() {
        measureCache.clear()
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
    layoutState.attachTo(null)
    children.forEach { it.detachLayoutParentRecursively() }
}

private object UiLayoutRevision {
    private var value = 0L

    fun next(): Long {
        value += 1L
        return value
    }
}

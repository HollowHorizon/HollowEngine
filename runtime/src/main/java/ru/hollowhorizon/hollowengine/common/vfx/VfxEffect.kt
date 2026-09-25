package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.Serializable

/** One authored effect: a tree of nodes and timeline that drives them. */
@Serializable
data class VfxEffect(
    val version: Int = CURRENT_VERSION,
    val nodes: List<VfxNodeSpec> = emptyList(),
    val timeline: VfxTimelineSpec = VfxTimelineSpec(),
) {
    /** Every node of tree, parents before children. */
    fun walk(): List<VfxNodeSpec> = nodes.flatMap { it.walkSelf() }

    fun node(id: String): VfxNodeSpec? = walk().firstOrNull { it.id == id }

    /** The node [id] sits under, or null for a root node or an unknown id. */
    fun parentOf(id: String): VfxNodeSpec? = walk().firstOrNull { parent -> parent.children.any { it.id == id } }

    /** Every expression in the file, so they compile as one unit. */
    fun expressions(): List<String> = walk().flatMap { it.expressions() }.filter { it.isNotBlank() }.distinct()

    /** The effect with [node] put in place of the node with the same id, wherever it sits. */
    fun withNode(node: VfxNodeSpec): VfxEffect = copy(nodes = nodes.map { it.replacing(node) })

    fun withoutNode(id: String): VfxEffect = copy(nodes = nodes.mapNotNull { it.removing(id) })

    /** [child] added under [parentId], or at the root when it is null. */
    fun withChild(parentId: String?, child: VfxNodeSpec): VfxEffect {
        if (parentId == null) return copy(nodes = nodes + child)
        return copy(nodes = nodes.map { it.adding(parentId, child) })
    }

    /** The effect with every node placed at its origin, switched on and unnamed: what placement leaves. */
    fun withoutPlacement(): VfxEffect = copy(nodes = nodes.map { it.withoutPlacement() })

    companion object {
        const val CURRENT_VERSION = 1

        val EMPTY = VfxEffect()
    }
}

private fun VfxNodeSpec.withoutPlacement(): VfxNodeSpec = withCommon(
    name = "",
    enabled = true,
    transform = VfxTransform.IDENTITY,
    children = children.map { it.withoutPlacement() },
)

private fun VfxNodeSpec.walkSelf(): List<VfxNodeSpec> = buildList {
    add(this@walkSelf)
    children.forEach { addAll(it.walkSelf()) }
}

private fun VfxNodeSpec.replacing(node: VfxNodeSpec): VfxNodeSpec {
    if (id == node.id) return node
    if (children.isEmpty()) return this
    return withCommon(children = children.map { it.replacing(node) })
}

private fun VfxNodeSpec.removing(target: String): VfxNodeSpec? {
    if (id == target) return null
    if (children.isEmpty()) return this
    return withCommon(children = children.mapNotNull { it.removing(target) })
}

private fun VfxNodeSpec.adding(parentId: String, child: VfxNodeSpec): VfxNodeSpec {
    if (id == parentId) return withCommon(children = children + child)
    if (children.isEmpty()) return this
    return withCommon(children = children.map { it.adding(parentId, child) })
}

@Serializable
data class VfxTrackCurve(
    val channel: Int = 0,
    val visible: Boolean = true,
    val keys: List<VfxKey> = emptyList(),
)

/**
 * One animated property of one node. One curve per scalar component of it.
 */
@Serializable
data class VfxTrack(
    val node: String = "",
    val property: VfxProperty = VfxProperty.ENABLED,
    val curves: List<VfxTrackCurve> = emptyList(),
)

@Serializable
data class VfxTimelineSpec(
    val duration: Float = 5f,
    val loop: Boolean = true,
    val tracks: List<VfxTrack> = emptyList(),
) {
    fun track(node: String, property: VfxProperty): VfxTrack? =
        tracks.firstOrNull { it.node == node && it.property == property }

    val isEmpty: Boolean get() = tracks.isEmpty()
}

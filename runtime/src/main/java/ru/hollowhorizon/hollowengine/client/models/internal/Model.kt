package ru.hollowhorizon.hollowengine.client.models.internal

import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationClip

data class Model(
    val scene: Int,
    val scenes: List<Scene>,
    val materials: Set<Material>,
    val animations: List<AnimationClip> = emptyList(),
) {
    var isBlockBench = false

    fun walkNodes(): Sequence<NodeDefinition> {
        return sequence {
            suspend fun SequenceScope<NodeDefinition>.walk(node: NodeDefinition) {
                yield(node)
                node.children.forEach { walk(it) }
            }
            scenes.flatMap { it.nodes }.forEach { walk(it) }
        }
    }

    fun findNodeByIndex(index: Int): NodeDefinition? {
        return walkNodes().find { it.index == index }
    }

    fun node(id: Int) = findNodeByIndex(id) ?: error("Node $id not found")

    fun findNodeByName(name: String): NodeDefinition? {
        return walkNodes().find { it.name == name }
    }
}

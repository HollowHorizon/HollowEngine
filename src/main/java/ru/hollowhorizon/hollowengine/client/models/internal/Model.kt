package ru.hollowhorizon.hollowengine.client.models.internal

import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline

data class Model(
    val scene: Int,
    val scenes: List<Scene>,
    val materials: Set<Material>,
) {
    var isBlockBench = false

    val pipeline = ListRenderPipeline()

    fun initGl() {
        walkNodes().forEach { node ->
            node.mesh?.primitives?.forEach { it.init() }
        }
    }

    fun walkNodes(): Sequence<Node> {
        return sequence {
            suspend fun SequenceScope<Node>.walk(node: Node) {
                yield(node)
                node.children.forEach { walk(it) }
            }
            scenes.flatMap { it.nodes }.forEach { walk(it) }
        }
    }

    fun findNodeByIndex(index: Int): Node? {
        return walkNodes().find { it.index == index }
    }

    fun node(id: Int) = findNodeByIndex(id) ?: error("Node $id not found")

    fun findNodeByName(name: String): Node? {
        return walkNodes().find { it.name == name }
    }
}
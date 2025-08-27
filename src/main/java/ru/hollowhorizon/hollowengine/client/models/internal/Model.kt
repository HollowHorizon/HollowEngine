package ru.hollowhorizon.hollowengine.client.models.internal

data class Model(
    val scene: Int,
    val scenes: List<Scene>,
    val materials: Set<Material>,
) {
    var isBlockBench = false

    fun initGl() {
        walkNodes().forEach { it.mesh?.primitives?.forEach { it.init() } }
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

    fun findNodeByName(name: String): Node? {
        return walkNodes().find { it.name == name }
    }
}
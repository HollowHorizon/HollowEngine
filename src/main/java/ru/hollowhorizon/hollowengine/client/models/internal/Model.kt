package ru.hollowhorizon.hollowengine.client.models.internal

data class Model(
    val scene: Int,
    val scenes: List<Scene>,
    val materials: Set<Material>,
) {
    var isBlockBench = false
    var useVAO = false

    fun initGl() {
        walkNodes().forEach {
            it.mesh?.primitives?.forEach { it.init() }
            useVAO = useVAO || it.mesh?.useBatching == false
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
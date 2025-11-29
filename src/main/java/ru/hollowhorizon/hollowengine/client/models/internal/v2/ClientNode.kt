package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.scene.TrsTransformF
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline

class ClientNode(
    val definition: NodeDefinition,
    parent: RuntimeNode?,
) : RuntimeNode(definition.name ?: "Unnamed_${definition.index}", TrsTransformF().set(definition.baseTransform), parent), RenderNode {

    override val children: MutableList<RuntimeNode> = definition.children.map {
        ClientNode(it, this)
    }.toMutableList()

    override fun collectCommands(pipeline: RenderPipeline) {
        pipeline.addInitializable {
            updateGlobalMatrix()
        }
        definition.mesh?.primitives?.forEach { primitive ->
            primitive.setupPipeline(pipeline, { definition.skin!!.compute(definition) }, ::globalMatrix)
        }
        children.filterIsInstance<RenderNode>().forEach { it.collectCommands(pipeline) }
    }
}

fun ClientNode.walk(): List<ClientNode> = buildList {
    add(this@walk)
    children.filterIsInstance<ClientNode>().forEach {
        addAll(it.walk())
    }
}
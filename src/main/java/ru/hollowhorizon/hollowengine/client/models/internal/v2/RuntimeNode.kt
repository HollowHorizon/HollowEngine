package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.scene.TrsTransformF
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline

abstract class Attachment(val parent: Attachment? = null) {
    val transform = TrsTransformF()
    val globalMatrix = MutableMat4f()

    open fun updateGlobalMatrix() {
        val localM = transform.matrixF
        if (parent != null) {
            globalMatrix.set(parent.globalMatrix)
            globalMatrix.mul(localM)
        } else {
            globalMatrix.set(localM)
        }
    }

    open fun collectCommands(pipeline: RenderPipeline) {
        pipeline.onUpdate { updateGlobalMatrix() }
    }
}

open class RuntimeNode(
    val definition: NodeDefinition,
    parent: Attachment?,
) : Attachment(parent) {
    val name: String = definition.name ?: parent?.let { "Node_${definition.index}" } ?: "Root"
    var isVisible = true
        set(value) {
            field = value
            children.forEach { it.isVisible = value }
        }

    init {
        transform.set(definition.baseTransform)
    }

    val attachments = arrayListOf<Attachment>()

    val children = definition.children.map {
        RuntimeNode(it, this)
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        definition.mesh?.primitives?.forEach { primitive ->
            primitive.setupPipeline(pipeline, { definition.skin!!.compute(definition) }, ::globalMatrix, ::isVisible)
        }
        attachments.forEach {
            it.collectCommands(pipeline)
        }
        children.forEach {
            it.collectCommands(pipeline)
        }
    }

    fun child(name: String): RuntimeNode = children.single { it.name == name }
}

fun RuntimeNode.walk(): List<RuntimeNode> = buildList {
    add(this@walk)
    children.forEach {
        addAll(it.walk())
    }
}
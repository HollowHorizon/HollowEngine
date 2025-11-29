package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.scene.TrsTransformF
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline

abstract class RuntimeNode(
    val name: String,
    val transform: TrsTransformF = TrsTransformF(),
    var parent: RuntimeNode? = null,
) {

    val globalMatrix = MutableMat4f()

    abstract val children: MutableList<RuntimeNode>

    open fun updateGlobalMatrix() {
        val localM = transform.matrixF
        if (parent != null) {
            globalMatrix.set(parent!!.globalMatrix)
            globalMatrix.mul(localM)
        } else {
            globalMatrix.set(localM)
        }
        children.forEach { it.updateGlobalMatrix() }
    }

    fun child(name: String): RuntimeNode = children.single { it.name == name }
}

interface RenderNode {
    fun collectCommands(pipeline: RenderPipeline)
}
package ru.hollowhorizon.hollowengine.client.models.internal

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.scene.TrsTransformF
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline

open class Node(
    val index: Int,
    val children: MutableList<Node>,
    val transform: TrsTransformF,
    val mesh: Mesh? = null,
    val skin: Skin? = null,
    val name: String? = null,
) {
    val baseTransform = TrsTransformF().apply {
        translate(transform.translation)
        rotate(transform.rotation)
        scale(transform.scale)
    }

    var parent: Node? = null
    val root: Node by lazy { parent?.root ?: this }

    val localMatrix get() = transform.matrixF
    val globalMatrix = MutableMat4f()

    val globalRotation: QuatF
        get() {
            var rotation = parent?.globalRotation ?: return transform.rotation
            transform.apply {
                rotation = rotation.mul(this.rotation, MutableQuatF())
            }
            return rotation
        }


    fun allBones(): Set<Node> = setOf(this) + children.flatMap { it.allBones() }
    val path: String get() = parent?.let { it.name + "/" + name } ?: name ?: "Unnamed Bone"

    override fun toString(): String {
        return "Node $name [Mesh: $mesh, Skin: $skin]"
    }

    open fun setupPipeline(pipeline: RenderPipeline) {
        val parent = parent
        if (parent == null) {
            pipeline.addInitializable {
                globalMatrix.set(localMatrix)
            }
        } else {
            pipeline.addInitializable {
                globalMatrix.set(parent.globalMatrix)
                globalMatrix.mul(localMatrix)
            }
        }
        children.forEach { it.setupPipeline(pipeline) }
        mesh?.primitives?.forEach { it.setupPipeline(this, pipeline) }
    }
}
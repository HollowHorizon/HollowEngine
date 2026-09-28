package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

/** A fresh node id; readable on purpose, because it appears in timeline tracks and in save files. */
fun newVfxNodeId(prefix: String): String = "$prefix-${Integer.toHexString((0..0xFFFFFF).random())}"

/** A node placement inside the effect. */
@Serializable
data class VfxTransform(
    val position: Vec3f = Vec3f.ZERO,
    val rotation: Vec3f = Vec3f.ZERO,
    val scale: Vec3f = Vec3f.ONES,
) {
    companion object {
        val IDENTITY = VfxTransform()
    }
}

/**
 * One node of an effect.
 *
 * Node kinds live in [VfxNodeTypes], so an addon can add its own.
 */
@Serializable
abstract class VfxNodeSpec {
    abstract val id: String
    abstract val name: String
    abstract val enabled: Boolean
    abstract val transform: VfxTransform
    abstract val children: List<VfxNodeSpec>

    /** A copy with the shared fields replaced, whichever kind of node this is. */
    abstract fun withCommon(
        id: String = this.id,
        name: String = this.name,
        enabled: Boolean = this.enabled,
        transform: VfxTransform = this.transform,
        children: List<VfxNodeSpec> = this.children,
    ): VfxNodeSpec

    /** Every expression this node evaluates, so a file compiles them all in one pass. */
    open fun expressions(): List<String> = emptyList()

    /** What of this node, besides its placement, the timeline can drive. */
    open fun animatables(): List<VfxAnimatable> = emptyList()
}

/**
 * A node that draws on every particle of the emitter it sits directly under, and once in its own
 * place anywhere else.
 */
interface VfxParticleRendererSpec {
    /** What the node gives each particle it draws; a node drawing itself does not read it. */
    val particle: VfxAppearance

    fun withParticle(particle: VfxAppearance): VfxNodeSpec
}

/**
 * The size, turn and color a renderer gives each particle of its emitter, read again every step for
 * as long as the particle lives. Every renderer on one emitter has its own, so a spark and its glow
 * can differ.
 */
@Serializable
data class VfxAppearance(
    val size: VfxVec3Value = VfxVec3Value.all(0.25f),
    val uniformSize: Boolean = true,
    val rotation: VfxVec3Value = VfxVec3Value.ZERO,
    val color: VfxColorValue = VfxColorValue.WHITE,
) {
    fun expressions(): List<String> = size.sources() + rotation.sources() + color.sources()

    fun animatables(): List<VfxAnimatable> = listOf(
        VfxAnimatables.vector(VfxProperty.SIZE, "size", size),
        VfxAnimatables.rotation(VfxProperty.SPIN, "spin", rotation),
        VfxAnimatables.color(VfxProperty.COLOR, "color", color),
    )
}

/** Nothing but a place in the tree: moves, turns, scales and switches off everything under it. */
@Serializable
@SerialName("hollowengine:vfx/group")
data class VfxGroupSpec(
    override val id: String = newVfxNodeId("group"),
    override val name: String = "Group",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
) : VfxNodeSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)
}

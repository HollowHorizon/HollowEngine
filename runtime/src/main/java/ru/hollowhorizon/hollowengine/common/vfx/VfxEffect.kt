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
    /** An id from [VfxAnimatables]. */
    val property: String = "",
    val curves: List<VfxTrackCurve> = emptyList(),
)

@Serializable
data class VfxTimelineSpec(
    val duration: Float = 5f,
    val loop: Boolean = true,
    val tracks: List<VfxTrack> = emptyList(),
) {
    fun track(node: String, property: String): VfxTrack? =
        tracks.firstOrNull { it.node == node && it.property == property }

    val isEmpty: Boolean get() = tracks.isEmpty()
}

/** What a timeline track can drive. */
enum class VfxAnimatableKind(val channels: Int) {
    /** On or off, held between keys instead of interpolated. */
    TOGGLE(1), FLOAT(1), VEC3(3), ROTATION(3), COLOR(4),
}

/**
 * One property a timeline may animate, and how to read its authored value as the starting point.
 */
class VfxAnimatable(
    val id: String,
    val titleKey: String,
    val kind: VfxAnimatableKind,
    val appliesTo: (VfxNodeSpec) -> Boolean,
    val ownerTitleKey: String? = null,
    val read: (VfxNodeSpec) -> FloatArray,
)

/**
 * The properties, that timeline can animate.
 */
object VfxAnimatables {
    const val ENABLED = "enabled"
    const val POSITION = "transform.position"
    const val ROTATION = "transform.rotation"
    const val SCALE = "transform.scale"
    const val RATE = "emission.rate"
    const val SHAPE_RADIUS = "shape.radius"
    const val SHAPE_EXTENTS = "shape.extents"
    const val SHAPE_ANGLE = "shape.angle"
    const val SHAPE_THICKNESS = "shape.thickness"
    const val LIFETIME = "spawn.lifetime"
    const val SPEED = "spawn.speed"
    const val OFFSET = "spawn.offset"
    const val INHERIT_VELOCITY = "spawn.inherit_velocity"
    const val SIZE = "appearance.size"
    const val SPIN = "appearance.rotation"
    const val COLOR = "appearance.color"
    const val GRAVITY = "motion.gravity"
    const val DRAG = "motion.drag"

    private const val MODULE_PREFIX = "modules."

    fun moduleProperty(moduleId: String, field: String): String = "$MODULE_PREFIX$moduleId.$field"

    private val common: List<VfxAnimatable> = listOf(
        VfxAnimatable(ENABLED, key("property_enabled"), VfxAnimatableKind.TOGGLE, { true }) {
            floatArrayOf(if (it.enabled) 1f else 0f)
        },
        VfxAnimatable(POSITION, key("property_position"), VfxAnimatableKind.VEC3, { true }) { node ->
            node.transform.position.let { floatArrayOf(it.x, it.y, it.z) }
        },
        VfxAnimatable(ROTATION, key("property_rotation"), VfxAnimatableKind.ROTATION, { true }) { node ->
            node.transform.rotation.let { floatArrayOf(it.x, it.y, it.z) }
        },
        VfxAnimatable(SCALE, key("property_scale"), VfxAnimatableKind.VEC3, { true }) { node ->
            node.transform.scale.let { floatArrayOf(it.x, it.y, it.z) }
        },
    )

    private val emitter: List<VfxAnimatable> = listOf(
        scalar(RATE, "rate") { it.emission.rate },
        scalar(SHAPE_RADIUS, "radius") { it.shape.radius },
        vector(SHAPE_EXTENTS, "extents") { it.shape.extents },
        scalar(SHAPE_ANGLE, "angle") { it.shape.angle },
        scalar(SHAPE_THICKNESS, "thickness") { it.shape.thickness },
        scalar(LIFETIME, "lifetime") { it.spawn.lifetime },
        scalar(SPEED, "speed") { it.spawn.speed },
        vector(OFFSET, "spawn_offset") { it.spawn.offset },
        scalar(INHERIT_VELOCITY, "inherit_velocity") { it.spawn.inheritVelocity },
        vector(SIZE, "size") { it.appearance.size },
        VfxAnimatable(SPIN, key("spin"), VfxAnimatableKind.ROTATION, { it is VfxEmitterSpec }) {
            (it as VfxEmitterSpec).appearance.rotation.constants()
        },
        VfxAnimatable(COLOR, key("color"), VfxAnimatableKind.COLOR, { it is VfxEmitterSpec }) {
            (it as VfxEmitterSpec).appearance.color.constants()
        },
        scalar(GRAVITY, "gravity") { it.motion.gravity },
        scalar(DRAG, "drag") { it.motion.drag },
    )

    /** Everything the timeline can drive on [node], its modules included. */
    fun forNode(node: VfxNodeSpec): List<VfxAnimatable> = buildList {
        addAll(common)
        if (node !is VfxEmitterSpec) return@buildList
        addAll(emitter)
        node.modules.forEach { module ->
            val ownerKey = VfxModuleTypes.of(module)?.titleKey
            module.animatables().forEach { field ->
                add(
                    VfxAnimatable(
                        id = moduleProperty(module.id, field.field),
                        titleKey = field.titleKey,
                        kind = field.kind,
                        appliesTo = { it is VfxEmitterSpec && it.module(module.id) != null },
                        read = { spec ->
                            (spec as? VfxEmitterSpec)?.module(module.id)?.let(field.read)
                                ?: FloatArray(field.kind.channels)
                        },
                        ownerTitleKey = ownerKey,
                    )
                )
            }
        }
    }

    fun of(node: VfxNodeSpec, id: String): VfxAnimatable? = forNode(node).firstOrNull { it.id == id }

    private fun key(name: String) = "hollowengine.gui.vfx.$name"

    private fun scalar(id: String, title: String, get: (VfxEmitterSpec) -> VfxValue) =
        VfxAnimatable(id, key(title), VfxAnimatableKind.FLOAT, { it is VfxEmitterSpec }) {
            floatArrayOf(get(it as VfxEmitterSpec).constantOr(0f))
        }

    private fun vector(id: String, title: String, get: (VfxEmitterSpec) -> VfxVec3Value) =
        VfxAnimatable(id, key(title), VfxAnimatableKind.VEC3, { it is VfxEmitterSpec }) {
            get(it as VfxEmitterSpec).constants()
        }
}

/** The expression sources inside a value, for the one-pass compile of a file. */
fun VfxValue.sources(): List<String> = when (this) {
    is VfxValue.Expr -> listOf(source).filter { it.isNotBlank() }
    else -> emptyList()
}

fun VfxVec3Value.sources(): List<String> = x.sources() + y.sources() + z.sources()

fun VfxColorValue.sources(): List<String> = when (this) {
    is VfxColorValue.Channels -> r.sources() + g.sources() + b.sources() + a.sources()
    else -> emptyList()
}

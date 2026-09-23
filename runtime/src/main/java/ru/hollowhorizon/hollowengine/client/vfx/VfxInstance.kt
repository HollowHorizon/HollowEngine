package ru.hollowhorizon.hollowengine.client.vfx

import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.data.NbtDataStore
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.*
import kotlin.random.Random

/**
 * One node of a playing effect: where it is, whether it is on, and whatever it runs.
 */
class VfxNodeRuntime(
    val spec: VfxNodeSpec,
    val parent: VfxNodeRuntime?,
    instance: VfxInstance,
    expressions: VfxExpressions,
    seed: Int,
    driven: Map<String, Int>,
) {
    val frame = VfxFrame()

    /** What the author wrote for this node; the timeline starts from it every frame. */
    var authoredEnabled: Boolean = spec.enabled
    var authoredTransform: VfxTransform = spec.transform

    var enabled: Boolean = spec.enabled
    var transform: VfxTransform = spec.transform

    /**
     * What the timeline writes into the values of this node. Only the properties that actually have
     * a track get one.
     */
    private val drives: Map<String, VfxDrive> = driven.mapValues { (_, channels) -> VfxDrive(channels) }

    fun drive(property: String): VfxDrive? = drives[property]

    val emitter: VfxEmitter? = (spec as? VfxEmitterSpec)?.let { VfxEmitter(it, instance, this, expressions, seed) }

    /** The model a mesh emitter draws, loaded the first time it is actually needed. */
    val meshModel: ModelAttachment? by lazy {
        val model = (spec as? VfxMeshEmitterSpec)?.model?.takeIf { it.isNotBlank() } ?: return@lazy null
        ModelAttachment(model)
    }

    val children: List<VfxNodeRuntime> = spec.children.mapIndexed { index, child ->
        VfxNodeRuntime(
            child,
            this,
            instance,
            expressions,
            seed + index * 977 + 13,
            instance.drivenProperties(child.id),
        )
    }

    /** Whether this node and every node above it is on. */
    val isActive: Boolean get() = enabled && (parent?.isActive ?: true)

    fun place(root: VfxFrame) {
        frame.setCombined(parent?.frame ?: root, transform)
        emitter?.refreshSpace()
        children.forEach { it.place(root) }
    }

    fun walk(): List<VfxNodeRuntime> = buildList {
        add(this@VfxNodeRuntime)
        children.forEach { addAll(it.walk()) }
    }

    fun reset() {
        enabled = authoredEnabled
        transform = authoredTransform
        children.forEach { it.reset() }
    }
}

/**
 * One playing effect.
 *
 * Time comes from the caller rather than from a clock of its own, because the same instance plays in
 * the world off the game clock and in the editor off a scrubbing timeline.
 */
class VfxInstance(
    val effect: VfxEffect,
    val asset: String,
    val environment: VfxEnvironment = VfxEnvironment.EMPTY,
    seed: Int = Random.nextInt(),
) {
    private val expressions = VfxExpressions.compile(effect.expressions(), asset)

    /** Which properties of which node have a track, and how many channels each carries. */
    private val driven: Map<String, Map<String, Int>> =
        effect.timeline.tracks.groupBy { it.node }.mapValues { (nodeId, tracks) ->
                val node = effect.node(nodeId)
                tracks.mapNotNull { track ->
                    if (node == null || track.curves.none { it.keys.isNotEmpty() }) return@mapNotNull null
                    val animatable = VfxAnimatables.of(node, track.property) ?: return@mapNotNull null
                    track.property to animatable.kind.channels
                }.toMap()
            }

    internal fun drivenProperties(nodeId: String): Map<String, Int> = driven[nodeId].orEmpty()

    /** The frame the whole effect sits in; a bone or a moving entity writes into it. */
    val root = VfxFrame()

    /** Where effect space sits in the world. */
    var origin: Vec3 = Vec3.ZERO
        private set

    /** Where world-space particles are measured from; fixed, so they do not follow a moving origin. */
    var anchor: Vec3 = Vec3.ZERO
        private set

    /** [origin] minus [anchor], the only part of the two a float can safely hold. */
    val originMinusAnchor = MutableVec3f()

    val nodes: List<VfxNodeRuntime> = effect.nodes.mapIndexed { index, node ->
        VfxNodeRuntime(node, null, this, expressions, seed + index * 7919, drivenProperties(node.id))
    }

    private val allNodes: List<VfxNodeRuntime> = nodes.flatMap { it.walk() }

    val emitters: List<VfxEmitter> = allNodes.mapNotNull { it.emitter }

    private val timeline = VfxTimelinePlayer(effect.timeline, allNodes)

    /** Seconds since the effect started playing. */
    var time: Float = 0f
        private set

    /** How fast the origin is moving, for particles that inherit it. */
    var velocityX: Float = 0f
        private set
    var velocityY: Float = 0f
        private set
    var velocityZ: Float = 0f
        private set

    var partialTick: Float = 0f
    var gameTime: Float = 0f

    /** The data store of the handle, flattened to the numbers expressions read as `d.`. */
    var data: Map<String, Float> = emptyMap()
        private set

    /** Whether anything here is lit by the world, so light is only looked up when it is used. */
    val readsLight: Boolean =
        effect.walk().filterIsInstance<VfxQuadEmitterSpec>().any { it.material.lighting == VfxLighting.WORLD }

    var isStopping: Boolean = false
        private set

    /** Whether everything has stopped and nothing is left on screen. */
    val isFinished: Boolean
        get() = isStopping && emitters.all { it.particles.count == 0 } || emitters.isNotEmpty() && emitters.all { it.isFinished }

    /** How many particles this effect may still create this frame; the scene refills it. */
    var budget: Int = Int.MAX_VALUE

    val particleCount: Int get() = emitters.sumOf { it.particles.count }

    init {
        moveTo(Vec3.ZERO, resetAnchor = true)
    }

    fun readData(store: NbtDataStore) {
        data = store.numericPaths()
    }

    /** Puts effect space somewhere in the world. The anchor only moves when asked. */
    fun moveTo(position: Vec3, resetAnchor: Boolean = false) {
        if (resetAnchor) anchor = position
        origin = position
        originMinusAnchor.set(
            (origin.x - anchor.x).toFloat(),
            (origin.y - anchor.y).toFloat(),
            (origin.z - anchor.z).toFloat(),
        )
    }

    /** Remembers how fast the origin is moving, for inherited velocity. */
    fun setVelocity(x: Float, y: Float, z: Float) {
        velocityX = x
        velocityY = y
        velocityZ = z
    }

    fun stop(immediate: Boolean) {
        isStopping = true
        if (immediate) emitters.forEach { it.clear() } else emitters.forEach { it.stop() }
    }

    /** Advances everything by [dt] seconds and leaves the emitters ready to draw. */
    fun update(dt: Float) {
        if (dt > 0f) time += dt
        pose()
        emitters.forEach { it.update(dt) }
    }

    /** Places every node for the current time: the authored transform, then the timeline over it. */
    private fun pose() {
        nodes.forEach { it.reset() }
        if (!timeline.isEmpty) timeline.apply(time)
        nodes.forEach { it.place(root) }
    }

    fun node(id: String): VfxNodeRuntime? = allNodes.firstOrNull { it.spec.id == id }

    /**
     * Takes the placement of [next] - where nodes are, whether they are on, what they are called -
     * without building the effect again. Returns false when [next] differs in anything else.
     */
    fun adoptPlacement(next: VfxEffect): Boolean {
        if (next.withoutPlacement() != effect.withoutPlacement()) return false
        allNodes.forEach { node ->
            val spec = next.node(node.spec.id) ?: return@forEach
            node.authoredTransform = spec.transform
            node.authoredEnabled = spec.enabled
        }
        pose()
        return true
    }

    /**
     * Back to the first moment. No particles, every emitter about to start its first loop, and
     * nodes where timeline has them at zero.
     */
    fun restart() {
        time = 0f
        isStopping = false
        emitters.forEach { it.reset() }
        pose()
    }

    /** Whether one more particle fits in the global budget. */
    fun requestParticle(): Boolean {
        if (budget <= 0) return false
        budget--
        return true
    }

    /** The packed light at a particle, or full bright when nothing here is lit by the world. */
    fun lightAt(emitter: VfxEmitter, slot: Int): Int {
        if (!readsLight) return FULL_BRIGHT
        val point = emitter.toEffectSpace(slot, lightCursor)
        return environment.lightAt(origin.x + point.x, origin.y + point.y, origin.z + point.z)
    }

    private val lightCursor = MutableVec3f()

    private companion object {
        const val FULL_BRIGHT = 0xF000F0
    }
}

/**
 * Plays the timeline of an effect onto its live nodes.
 *
 * Curves are sampled the same way the curve editor draws them.
 */
class VfxTimelinePlayer(private val spec: VfxTimelineSpec, nodes: List<VfxNodeRuntime>) {
    /** Curves paired with the channel they drive, resolved once. */
    private class PreparedTrack(
        val node: VfxNodeRuntime,
        val property: String,
        val channels: Int,
        val defaults: FloatArray,
        val curves: List<Pair<Int, VfxCurve>>,
        val drive: VfxDrive?,
    )

    private val tracks: List<PreparedTrack>
    private val values = FloatArray(4)

    val isEmpty: Boolean get() = tracks.isEmpty()

    init {
        val byId = nodes.associateBy { it.spec.id }
        tracks = spec.tracks.mapNotNull { track -> prepare(track, byId) }
    }

    fun apply(time: Float) {
        val local = if (spec.loop && spec.duration > 0f) time.mod(spec.duration) else time
        tracks.forEach { track -> apply(track, local) }
    }

    private fun prepare(track: VfxTrack, byId: Map<String, VfxNodeRuntime>): PreparedTrack? {
        val node = byId[track.node] ?: return null
        val property = VfxAnimatables.of(node.spec, track.property) ?: return null
        val channels = property.kind.channels

        val curves = track.curves.filter { it.visible && it.channel in 0 until channels && it.keys.isNotEmpty() }
            .map { it.channel to VfxCurve(it.keys) }
        if (curves.isEmpty()) return null

        val drive = node.drive(track.property)
        drive?.let { target -> curves.forEach { (channel, _) -> target.active[channel] = true } }

        return PreparedTrack(node, track.property, channels, property.read(node.spec), curves, drive)
    }

    private fun apply(track: PreparedTrack, time: Float) {
        for (channel in 0 until track.channels) {
            values[channel] = track.defaults.getOrElse(channel) { 0f }
        }

        val placement = when (track.property) {
            VfxAnimatables.POSITION -> track.node.transform.position
            VfxAnimatables.ROTATION -> track.node.transform.rotation
            VfxAnimatables.SCALE -> track.node.transform.scale
            else -> null
        }
        placement?.let {
            values[0] = it.x
            values[1] = it.y
            values[2] = it.z
        }
        track.curves.forEach { (channel, curve) ->
            values[channel] = curve.valueAt(time, values[channel])
        }

        when (track.property) {
            VfxAnimatables.ENABLED -> track.node.enabled = values[0] >= 0.5f
            VfxAnimatables.POSITION -> track.node.transform =
                track.node.transform.copy(position = Vec3f(values[0], values[1], values[2]))

            VfxAnimatables.ROTATION -> track.node.transform =
                track.node.transform.copy(rotation = Vec3f(values[0], values[1], values[2]))

            VfxAnimatables.SCALE -> track.node.transform =
                track.node.transform.copy(scale = Vec3f(values[0], values[1], values[2]))

            else -> track.drive?.let { drive ->
                for (channel in 0 until track.channels) drive.values[channel] = values[channel]
            }
        }
    }
}

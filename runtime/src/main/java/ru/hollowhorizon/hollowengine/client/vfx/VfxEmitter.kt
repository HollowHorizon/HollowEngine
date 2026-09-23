package ru.hollowhorizon.hollowengine.client.vfx

import ru.hollowhorizon.hollowengine.client.utils.math.rotateBy
import ru.hollowhorizon.hollowengine.common.utils.MutableColor
import ru.hollowhorizon.hollowengine.common.utils.math.MutableMat4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables
import ru.hollowhorizon.hollowengine.common.vfx.VfxDirectionMode
import ru.hollowhorizon.hollowengine.common.vfx.VfxEmitterSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxShapeKind
import ru.hollowhorizon.hollowengine.common.vfx.VfxSimulationSpace
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxUvAnimationSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxValue
import ru.hollowhorizon.hollowengine.common.vfx.VfxVec3Value
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * One emitter of a playing effect.
 */
class VfxEmitter(
    val spec: VfxEmitterSpec,
    val instance: VfxInstance,
    val node: VfxNodeRuntime,
    private val expressions: VfxExpressions,
    private val seed: Int,
) {
    val particles = VfxParticles(spec.emission.maxParticles.coerceIn(1, MAX_PARTICLES_PER_EMITTER))

    val frame: VfxFrame get() = node.frame

    val simToRender: MutableMat4f = MutableMat4f().setIdentity()

    val context = VfxEvalContext()

    private var random = Random(seed)

    private val rate = scalar(VfxAnimatables.RATE, spec.emission.rate, 0f)
    private val radius = scalar(VfxAnimatables.SHAPE_RADIUS, spec.shape.radius, 0f)
    private val angle = scalar(VfxAnimatables.SHAPE_ANGLE, spec.shape.angle, 0f)
    private val thickness = scalar(VfxAnimatables.SHAPE_THICKNESS, spec.shape.thickness, 1f)
    private val extents = vector(VfxAnimatables.SHAPE_EXTENTS, spec.shape.extents, 0f)

    private val lifetime = scalar(VfxAnimatables.LIFETIME, spec.spawn.lifetime, 1f)
    private val speed = scalar(VfxAnimatables.SPEED, spec.spawn.speed, 0f)
    private val offset = vector(VfxAnimatables.OFFSET, spec.spawn.offset, 0f)
    private val inheritVelocity = scalar(VfxAnimatables.INHERIT_VELOCITY, spec.spawn.inheritVelocity, 0f)

    private val appearance = spec.appearance
    private val size = vector(VfxAnimatables.SIZE, appearance.size, 1f, VfxRangeMode.PER_PARTICLE)
    private val spin = vector(VfxAnimatables.SPIN, appearance.rotation, 0f, VfxRangeMode.PER_PARTICLE)
    private val color = VfxSamplers.color(
        appearance.color,
        expressions,
        salt = VfxAnimatables.COLOR.hashCode(),
        drive = node.drive(VfxAnimatables.COLOR),
    )

    private val liveSize = isLive(VfxAnimatables.SIZE, appearance.size)
    private val liveSpin = isLive(VfxAnimatables.SPIN, appearance.rotation)
    private val liveColor = !VfxSamplers.isFixedPerParticle(appearance.color) ||
            node.drive(VfxAnimatables.COLOR) != null

    private val gravity = scalar(VfxAnimatables.GRAVITY, spec.motion.gravity, 0f, VfxRangeMode.PER_PARTICLE)
    private val drag = scalar(VfxAnimatables.DRAG, spec.motion.drag, 0f, VfxRangeMode.PER_PARTICLE)

    private val modules: List<VfxModule> = spec.modules
        .filter { it.enabled }
        .mapNotNull { module ->
            VfxModuleRuntimes.create(module, VfxModuleContext(spec, expressions) { field ->
                node.drive(VfxAnimatables.moduleProperty(module.id, field))
            })
        }

    /** The flipbook grid the renderer cuts the material region into; one cell without a flipbook. */
    val uvColumns: Int
    val uvRows: Int

    init {
        val flipbook = spec.modules.filterIsInstance<VfxUvAnimationSpec>().firstOrNull { it.enabled }
        uvColumns = flipbook?.columns?.coerceAtLeast(1) ?: 1
        uvRows = flipbook?.rows?.coerceAtLeast(1) ?: 1
    }

    /** An emitter whose node is off neither emits nor draws, but keeps the particles it has. */
    val enabled: Boolean get() = node.enabled

    var age: Float = 0f
        private set

    /** Seconds until the next loop starts, while the emitter is sleeping between loops. */
    private var sleepLeft: Float = 0f
    private var spawnDebt: Float = 0f
    private var born: Int = 0
    private var stopping: Boolean = false

    /** Whether this emitter will ever produce anything again. */
    val isFinished: Boolean
        get() = (stopping || isExpired()) && particles.count == 0

    private val spawnOrigin = MutableVec3f()
    private var spawnRotation: QuatF = QuatF.IDENTITY
    private val spawnScale = MutableVec3f(Vec3f.ONES)

    private val scratchPosition = MutableVec3f()
    private val scratchDirection = MutableVec3f()
    private val scratchVector = MutableVec3f()
    private val scratchColor = MutableColor(1f, 1f, 1f, 1f)

    /** Extra velocity the modules asked for, for this particle and this step only. */
    var stepVelocityX: Float = 0f
    var stepVelocityY: Float = 0f
    var stepVelocityZ: Float = 0f

    /** Where the particle was before it moved, so a collision can put it back. */
    var previousX: Float = 0f
    var previousY: Float = 0f
    var previousZ: Float = 0f

    init {
        reset()
    }

    /** Stops emitting; whatever is alive lives out its lifetime. */
    fun stop() {
        stopping = true
    }

    /** Stops emitting and drops everything alive at once. */
    fun clear() {
        particles.clear()
        stopping = true
    }

    /**
     * Puts the emitter back where it was when the effect was created.
     */
    fun reset() {
        particles.clear()
        random = Random(seed)
        context.rng = random
        context.variables.clear()
        age = 0f
        sleepLeft = 0f
        born = 0
        stopping = false
        spawnDebt = spec.emission.burst.toFloat()
        rollEmitterRandoms()
    }

    private fun rollEmitterRandoms() {
        repeat(4) { context.variables["emitter_random_${it + 1}"] = random.nextFloat() }
    }

    /**
     * Recomputes how the emitter maps to what is drawn, after the node has placed its frame.
     */
    fun refreshSpace() {
        when (spec.space) {
            VfxSimulationSpace.LOCAL -> {
                frame.toFollowedMatrix(simToRender, spec.inheritRotation, spec.inheritScale)
                spawnOrigin.set(Vec3f.ZERO)
                spawnRotation = if (spec.inheritRotation) QuatF.IDENTITY else frame.rotation
                spawnScale.set(if (spec.inheritScale) Vec3f.ONES else frame.scale)
            }

            VfxSimulationSpace.WORLD -> {
                val offset = instance.originMinusAnchor
                simToRender.setIdentity().translate(-offset.x, -offset.y, -offset.z)
                spawnOrigin.set(frame.position).add(offset)
                spawnRotation = frame.rotation
                spawnScale.set(frame.scale)
            }
        }
    }

    /**
     * Advances the emitter by [dt] seconds, split into steps no longer than the emitter asked for.
     */
    fun update(dt: Float) {
        if (dt <= 0f) return

        val maxStep = spec.maxStep.coerceAtLeast(MIN_STEP)
        val steps = ceil(dt / maxStep).toInt().coerceIn(1, MAX_SUBSTEPS)
        val step = dt / steps
        repeat(steps) { advance(step) }
    }

    private fun advance(dt: Float) {
        age += dt

        if (sleepLeft > 0f) {
            sleepLeft -= dt
            if (sleepLeft <= 0f) startLoop()
        } else if (isExpired()) {
            val emission = spec.emission
            if (emission.loop && !stopping) {
                sleepLeft = emission.loopDelay
                if (sleepLeft <= 0f) startLoop()
            }
        }

        if (enabled && !stopping && !isExpired() && sleepLeft <= 0f) emit(dt)

        fillEmitterContext()
        modules.forEach { it.beginStep(this, dt) }
        simulate(dt)
    }

    private fun startLoop() {
        age = 0f
        born = 0
        spawnDebt += spec.emission.burst.toFloat()
        rollEmitterRandoms()
    }

    private fun isExpired(): Boolean {
        val duration = spec.emission.duration
        return duration > 0f && age >= duration
    }

    private fun emit(dt: Float) {
        fillEmitterContext()
        val perSecond = rate.eval(context)
        if (perSecond > 0f) spawnDebt += perSecond * dt

        while (spawnDebt >= 1f) {
            spawnDebt -= 1f
            if (!spawn()) {
                spawnDebt = 0f
                return
            }
        }
    }

    /** Puts one particle into the world. Returns false when the emitter is full. */
    private fun spawn(): Boolean {
        if (!instance.requestParticle()) return false

        val slot = particles.allocate()
        if (slot < 0) return false

        fillEmitterContext()
        context.particleRandom = random.nextFloat()
        context.particleIndex = born.toFloat()
        context.age = 0f
        context.progress = 0f
        context.speed = 0f

        val life = lifetime.eval(context).coerceAtLeast(MIN_LIFETIME)
        context.lifetime = life

        pickShapePoint(scratchPosition, scratchDirection)
        offset.eval(context, scratchVector)
        scratchPosition.add(scratchVector)

        val launch = speed.eval(context)
        scratchDirection.set(
            scratchDirection.x * launch,
            scratchDirection.y * launch,
            scratchDirection.z * launch,
        )

        applySpawnSpace(scratchPosition, point = true)
        applySpawnSpace(scratchDirection, point = false)

        particles.positionX[slot] = scratchPosition.x
        particles.positionY[slot] = scratchPosition.y
        particles.positionZ[slot] = scratchPosition.z

        val inherit = inheritVelocity.eval(context)
        particles.velocityX[slot] = scratchDirection.x + instance.velocityX * inherit
        particles.velocityY[slot] = scratchDirection.y + instance.velocityY * inherit
        particles.velocityZ[slot] = scratchDirection.z + instance.velocityZ * inherit

        particles.age[slot] = 0f
        particles.lifetime[slot] = life
        particles.random[slot] = context.particleRandom
        particles.index[slot] = context.particleIndex
        particles.frame[slot] = 0f

        readSize(slot)
        readSpin(slot)
        readColor(slot)

        particles.light[slot] = instance.lightAt(this, slot)

        born++
        modules.forEach { it.onSpawn(this, slot) }
        return true
    }

    private fun simulate(dt: Float) {
        var slot = 0
        while (slot < particles.count) {
            particles.age[slot] += dt
            if (particles.age[slot] >= particles.lifetime[slot]) {
                particles.kill(slot)
                continue
            }

            fillParticleContext(slot)

            stepVelocityX = 0f
            stepVelocityY = 0f
            stepVelocityZ = 0f
            modules.forEach { it.onStep(this, slot, dt) }

            val fall = gravity.eval(context)
            if (fall != 0f) particles.velocityY[slot] -= fall * dt

            val friction = drag.eval(context)
            if (friction > 0f) {
                val keep = (1f - friction * dt).coerceIn(0f, 1f)
                particles.velocityX[slot] *= keep
                particles.velocityY[slot] *= keep
                particles.velocityZ[slot] *= keep
            }

            previousX = particles.positionX[slot]
            previousY = particles.positionY[slot]
            previousZ = particles.positionZ[slot]

            particles.positionX[slot] += (particles.velocityX[slot] + stepVelocityX) * dt
            particles.positionY[slot] += (particles.velocityY[slot] + stepVelocityY) * dt
            particles.positionZ[slot] += (particles.velocityZ[slot] + stepVelocityZ) * dt

            modules.forEach { it.onMoved(this, slot, dt) }

            if (slot < particles.count && particles.age[slot] >= particles.lifetime[slot]) {
                particles.kill(slot)
                continue
            }

            if (liveSize) readSize(slot)
            if (liveSpin) readSpin(slot)
            if (liveColor) readColor(slot)

            if (instance.readsLight) particles.light[slot] = instance.lightAt(this, slot)
            slot++
        }
    }

    private fun readSize(slot: Int) {
        if (appearance.uniformSize) size.evalUniform(context, scratchVector) else size.eval(context, scratchVector)
        particles.sizeX[slot] = scratchVector.x
        particles.sizeY[slot] = scratchVector.y
        particles.sizeZ[slot] = scratchVector.z
    }

    private fun readSpin(slot: Int) {
        spin.eval(context, scratchVector)
        particles.rotationX[slot] = scratchVector.x
        particles.rotationY[slot] = scratchVector.y
        particles.rotationZ[slot] = scratchVector.z
    }

    private fun readColor(slot: Int) {
        color.eval(context, scratchColor)
        particles.colorR[slot] = scratchColor.r
        particles.colorG[slot] = scratchColor.g
        particles.colorB[slot] = scratchColor.b
        particles.colorA[slot] = scratchColor.a
    }

    /** Fills context with what does not depend on a particular particle. */
    fun fillEmitterContext() {
        context.emitterAge = age
        context.emitterCount = particles.count.toFloat()
        context.effectTime = instance.time
        context.partialTick = instance.partialTick
        context.gameTime = instance.gameTime
        context.data = instance.data
    }

    /** Fills context for one particle, which is what every sampler reads. */
    fun fillParticleContext(slot: Int) {
        fillEmitterContext()
        val life = particles.lifetime[slot]
        context.age = particles.age[slot]
        context.lifetime = life
        context.progress = if (life > 0f) (particles.age[slot] / life).coerceIn(0f, 1f) else 1f
        context.positionX = particles.positionX[slot]
        context.positionY = particles.positionY[slot]
        context.positionZ = particles.positionZ[slot]
        val vx = particles.velocityX[slot]
        val vy = particles.velocityY[slot]
        val vz = particles.velocityZ[slot]
        context.velocityX = vx
        context.velocityY = vy
        context.velocityZ = vz
        context.speed = sqrt(vx * vx + vy * vy + vz * vz)
        context.particleRandom = particles.random[slot]
        context.particleIndex = particles.index[slot]
    }

    /** A particle position in effect space, which is what the world is read in. */
    fun toEffectSpace(slot: Int, into: MutableVec3f): MutableVec3f {
        into.set(particles.positionX[slot], particles.positionY[slot], particles.positionZ[slot])
        return simToRender.transform(into, 1f)
    }

    /** Turns a direction of effect space into the space this emitter simulates in. */
    fun toSimDirection(direction: Vec3f, into: MutableVec3f): MutableVec3f = when (spec.space) {
        VfxSimulationSpace.WORLD -> into.set(direction)
        VfxSimulationSpace.LOCAL -> {
            val turned = direction.rotateBy(frame.rotation.inverted())
            into.set(turned.x, turned.y, turned.z)
        }
    }

    /** A point of the emitter frame, in the space particles live in; how a module places a force. */
    fun nodeToSimPoint(point: Vec3f, into: MutableVec3f): MutableVec3f {
        into.set(point)
        applySpawnSpace(into, point = true)
        return into
    }

    fun nodeToSimDirection(direction: Vec3f, into: MutableVec3f): MutableVec3f {
        into.set(direction)
        applySpawnSpace(into, point = false)
        return into
    }

    /** Moves a birth position or direction out of node space into the space particles live in. */
    private fun applySpawnSpace(vector: MutableVec3f, point: Boolean) {
        vector.set(vector.x * spawnScale.x, vector.y * spawnScale.y, vector.z * spawnScale.z)
        if (spawnRotation !== QuatF.IDENTITY) {
            val rotated = (vector as Vec3f).rotateBy(spawnRotation)
            vector.set(rotated.x, rotated.y, rotated.z)
        }
        if (point) vector.add(spawnOrigin)
    }

    /**
     * Picks a spawn point on the shape and the way the particle leaves it.
     */
    private fun pickShapePoint(position: MutableVec3f, direction: MutableVec3f) {
        val shape = spec.shape
        position.set(Vec3f.ZERO)
        direction.set(shape.direction)

        when (shape.kind) {
            VfxShapeKind.POINT -> Unit

            VfxShapeKind.SPHERE -> {
                randomDirection(direction)
                val reach = radius.eval(context) * shellDepth(thickness.eval(context))
                position.set(direction.x * reach, direction.y * reach, direction.z * reach)
            }

            VfxShapeKind.BOX -> {
                extents.eval(context, scratchVector)
                position.set(
                    (random.nextFloat() * 2f - 1f) * scratchVector.x,
                    (random.nextFloat() * 2f - 1f) * scratchVector.y,
                    (random.nextFloat() * 2f - 1f) * scratchVector.z,
                )
            }

            VfxShapeKind.CONE -> {
                val around = random.nextFloat() * TAU
                val spread = tan(angle.eval(context).coerceIn(0f, 89f) * DEG_TO_RAD) * random.nextFloat()
                val reach = radius.eval(context) * sqrt(random.nextFloat()) * shellDepth(thickness.eval(context))
                position.set(cos(around) * reach, 0f, sin(around) * reach)
                direction.set(cos(around) * spread, 1f, sin(around) * spread).norm()
            }

            VfxShapeKind.DISC -> {
                val around = random.nextFloat() * TAU
                val reach = radius.eval(context) * sqrt(random.nextFloat()) * shellDepth(thickness.eval(context))
                position.set(cos(around) * reach, 0f, sin(around) * reach)
                direction.set(position).norm()
                if (direction.sqrLength() < 1.0e-6f) direction.set(shape.direction)
            }

            VfxShapeKind.LINE -> {
                extents.eval(context, scratchVector)
                val along = random.nextFloat()
                position.set(scratchVector.x * along, scratchVector.y * along, scratchVector.z * along)
            }
        }

        when (shape.directionMode) {
            VfxDirectionMode.FIXED -> direction.set(shape.direction)
            VfxDirectionMode.RANDOM -> randomDirection(direction)
            VfxDirectionMode.SHAPE -> Unit
        }
        if (direction.sqrLength() < 1.0e-6f) direction.set(Vec3f.Y_AXIS) else direction.norm()
    }

    private fun randomDirection(into: MutableVec3f) {
        val z = random.nextFloat() * 2f - 1f
        val around = random.nextFloat() * TAU
        val planar = sqrt((1f - z * z).coerceAtLeast(0f))
        into.set(cos(around) * planar, z, sin(around) * planar)
    }

    private fun shellDepth(thickness: Float): Float =
        if (thickness <= 0f) 1f else 1f - random.nextFloat() * thickness.coerceAtMost(1f)

    fun noiseSeed(axis: Int): Int = seed + axis * 6151

    private fun scalar(
        property: String,
        value: VfxValue,
        default: Float,
        range: VfxRangeMode = VfxRangeMode.FRESH,
    ): VfxFloatSampler = VfxSamplers.driven(
        VfxSamplers.scalar(value, expressions, default, range, property.hashCode()),
        node.drive(property),
    )

    private fun vector(
        property: String,
        value: VfxVec3Value,
        default: Float,
        range: VfxRangeMode = VfxRangeMode.FRESH,
    ): VfxVec3Sampler = VfxSamplers.vec3(value, expressions, default, range, property.hashCode(), node.drive(property))

    private fun isLive(property: String, value: VfxVec3Value): Boolean =
        !VfxSamplers.isFixedPerParticle(value) || node.drive(property) != null

    companion object {
        private const val MIN_STEP = 1f / 240f
        private const val MAX_SUBSTEPS = 8
        private const val MIN_LIFETIME = 0.01f
        private const val TAU = (PI * 2.0).toFloat()
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()

        /** A ceiling on what one emitter may ask for, whatever the file says. */
        const val MAX_PARTICLES_PER_EMITTER = 20_000
    }
}

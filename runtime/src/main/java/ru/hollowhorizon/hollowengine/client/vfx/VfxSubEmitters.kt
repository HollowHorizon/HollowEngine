package ru.hollowhorizon.hollowengine.client.vfx

import ru.hollowhorizon.hollowengine.common.utils.math.MutableMat4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxParticleEvent
import ru.hollowhorizon.hollowengine.common.vfx.VfxSubEmission

/**
 * Events a sub-emitter has been handed and not yet spawned from, already in the space it simulates
 * in: where, with how much velocity to add, the random number of the particle that had the event,
 * and how many particles to spawn.
 */
internal class VfxSubEvents {
    var size: Int = 0
        private set

    private var data = FloatArray(STRIDE * 16)

    fun add(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, random: Float, count: Int) {
        if ((size + 1) * STRIDE > data.size) data = data.copyOf(data.size * 2)
        val at = size * STRIDE
        data[at] = x
        data[at + 1] = y
        data[at + 2] = z
        data[at + 3] = vx
        data[at + 4] = vy
        data[at + 5] = vz
        data[at + 6] = random
        data[at + 7] = count.toFloat()
        size++
    }

    fun x(event: Int) = data[event * STRIDE]
    fun y(event: Int) = data[event * STRIDE + 1]
    fun z(event: Int) = data[event * STRIDE + 2]
    fun velocityX(event: Int) = data[event * STRIDE + 3]
    fun velocityY(event: Int) = data[event * STRIDE + 4]
    fun velocityZ(event: Int) = data[event * STRIDE + 5]
    fun random(event: Int) = data[event * STRIDE + 6]
    fun count(event: Int) = data[event * STRIDE + 7].toInt()

    fun clear() {
        size = 0
    }

    private companion object {
        const val STRIDE = 8
    }
}

/**
 * What makes an emitter a sub-emitter: the emitter it listens to, and the events it was handed.
 */
internal class VfxSubEmitterInput(
    private val spec: VfxSubEmission,
    val source: VfxEmitter,
    node: VfxNodeRuntime,
) {
    val events = VfxSubEvents()

    private val count = VfxSamplers.scalar(spec.count, node.expressions, 0f, VfxRangeMode.PER_PARTICLE, COUNT_SALT)
    private val inheritVelocity =
        VfxSamplers.scalar(spec.inheritVelocity, node.expressions, 0f, VfxRangeMode.PER_PARTICLE, VELOCITY_SALT)

    /** How many particles an [VfxParticleEvent.ALIVE] sub-emitter still owes each particle of [source]. */
    private val debtChannel = "sub:${node.spec.id}"

    /** The space [source] simulates in, to the one this emitter does; refreshed as the frames are placed. */
    private val sourceToOwn = MutableMat4f()
    private val renderToOwn = MutableMat4f()
    private val point = MutableVec3f()
    private val velocity = MutableVec3f()

    fun place(ownSimToRender: MutableMat4f) {
        ownSimToRender.invert(renderToOwn)
        renderToOwn.mul(source.simToRender, sourceToOwn)
    }

    /** A particle of [source] in [slot] had [event]; its context is filled for it. */
    fun take(event: VfxParticleEvent, slot: Int, dt: Float) {
        val particles = source.particles
        if (spec.event == VfxParticleEvent.ALIVE && event == VfxParticleEvent.SPAWN) {
            particles.channel(debtChannel)[slot] = 0f
        }
        if (event != spec.event) return

        val context = source.context
        val spawned = if (event == VfxParticleEvent.ALIVE) {
            val debt = particles.channel(debtChannel)
            debt[slot] += count.eval(context).coerceAtLeast(0f) * dt
            debt[slot].toInt().also { debt[slot] -= it }
        } else {
            count.eval(context).coerceAtLeast(0f).toInt()
        }
        if (spawned <= 0) return

        point.set(particles.positionX[slot], particles.positionY[slot], particles.positionZ[slot])
        sourceToOwn.transform(point, 1f)
        val inherit = inheritVelocity.eval(context)
        velocity.set(
            particles.velocityX[slot] * inherit,
            particles.velocityY[slot] * inherit,
            particles.velocityZ[slot] * inherit,
        )
        sourceToOwn.transform(velocity, 0f)

        events.add(point.x, point.y, point.z, velocity.x, velocity.y, velocity.z, particles.random[slot], spawned)
    }

    private companion object {
        const val COUNT_SALT = 0x5b1
        const val VELOCITY_SALT = 0x5b2
    }
}

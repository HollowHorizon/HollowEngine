package ru.hollowhorizon.hollowengine.client.ui.particles

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/**
 * A bounded, world-independent particle simulation owned by one drawing area.
 * Simulation and drawing belong to the render thread. Only [frameTimeNanos], [paused] and
 * [emitting] may be written from the Compose frame builder. Invisible areas do no simulation work.
 * Use [rememberUiParticleSystem] in Compose, or [advance] from a custom host's frame loop.
 */
class UiParticleSystem(
    emitters: List<UiParticleEmitter>,
    val maxParticles: Int = 512,
    val fixedTimeStep: Double = 1.0 / 60.0,
    val maxFrameTime: Double = 0.25,
    seed: Int = Random.nextInt(),
) {
    val emitters = emitters.toList()
    private val particles = ArrayList<UiParticle>()
    private val pool = ArrayDeque<UiParticle>()
    private val emission = DoubleArray(emitters.size)
    private val spawnScope = UiParticleSpawnScope(Random(seed))
    private val damping = FloatArray(emitters.size) { exp(-emitters[it].drag * fixedTimeStep).toFloat() }
    private var accumulator = 0.0
    private var initialized = false
    private var lastFrameTime: Long? = null

    @Volatile internal var frameTimeNanos: Long? = null
    @Volatile var paused = false
    @Volatile var emitting = true

    val particleCount: Int get() = particles.size
    internal val interpolation: Float get() = (accumulator / fixedTimeStep).toFloat()

    init {
        require(maxParticles > 0) { "Particle capacity must be positive" }
        require(fixedTimeStep.isFinite() && fixedTimeStep > 0.0) { "Time step must be finite and positive" }
        require(maxFrameTime.isFinite() && maxFrameTime >= fixedTimeStep) { "Frame limit must cover a time step" }
    }

    /** Clears live particles and timing; the configured initial bursts run again on the next advance. */
    fun reset() {
        pool.addAll(particles)
        particles.clear()
        emission.fill(0.0)
        accumulator = 0.0
        initialized = false
        lastFrameTime = null
    }

    /** Explicit host update. Long gaps are capped, with no deferred catch-up or emission backlog. */
    fun advance(deltaSeconds: Double, width: Float, height: Float) {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0.0) { "Elapsed time must be finite and non-negative" }
        require(width.isFinite() && height.isFinite() && width >= 0f && height >= 0f) { "Area must be finite and non-negative" }
        spawnScope.width = width
        spawnScope.height = height
        if (paused || width == 0f || height == 0f) return
        if (!initialized) {
            initialized = true
            if (emitting) emitters.forEachIndexed { index, emitter -> spawn(index, emitter.initialCount) }
        }
        accumulator += min(deltaSeconds, maxFrameTime)
        while (accumulator + fixedTimeStep * 1e-9 >= fixedTimeStep) {
            tick()
            accumulator = (accumulator - fixedTimeStep).coerceAtLeast(0.0)
        }
    }

    internal fun prepare(width: Float, height: Float) {
        val time = frameTimeNanos ?: return
        val previous = lastFrameTime
        if (previous != null && time <= previous) return
        lastFrameTime = time
        advance(if (previous == null) 0.0 else (time - previous) / 1_000_000_000.0, width, height)
    }

    internal fun forEachParticle(block: (UiParticle) -> Unit) {
        for (index in particles.indices) block(particles[index])
    }

    private fun tick() {
        val delta = fixedTimeStep.toFloat()
        var destination = 0
        for (index in particles.indices) {
            val particle = particles[index]
            val emitter = emitters[particle.emitterIndex]
            particle.previousX = particle.x
            particle.previousY = particle.y
            particle.previousAge = particle.age
            particle.age += delta
            if (particle.age < particle.lifetime) {
                emitter.update(particle, delta)
                particle.velocityX = (particle.velocityX + emitter.accelerationX * delta) * damping[particle.emitterIndex]
                particle.velocityY = (particle.velocityY + emitter.accelerationY * delta) * damping[particle.emitterIndex]
                particle.x += particle.velocityX * delta
                particle.y += particle.velocityY * delta
            }
            if (particle.isAlive()) {
                particles[destination++] = particle
            } else {
                pool.addLast(particle)
            }
        }
        if (destination < particles.size) particles.subList(destination, particles.size).clear()
        if (!emitting) return
        emitters.forEachIndexed { index, emitter ->
            val total = emission[index] + emitter.rate * fixedTimeStep
            val count = floor(total)
            emission[index] = total - count
            spawn(index, min(count, maxParticles.toDouble()).toInt())
        }
    }

    private fun spawn(emitterIndex: Int, count: Int) {
        val emitter = emitters[emitterIndex]
        repeat(min(count, maxParticles - particles.size)) {
            val particle = pool.removeLastOrNull() ?: UiParticle()
            particle.reset()
            particle.emitterIndex = emitterIndex
            particle.randomFrame = spawnScope.random.nextFloat()
            emitter.spawn(particle, spawnScope)
            particle.previousX = particle.x
            particle.previousY = particle.y
            if (particle.isAlive()) particles.add(particle) else pool.addLast(particle)
        }
    }

    private fun UiParticle.isAlive(): Boolean = lifetime.isFinite() && lifetime > age &&
            x.isFinite() && y.isFinite() && velocityX.isFinite() && velocityY.isFinite() &&
            size.isFinite() && size >= 0f
}

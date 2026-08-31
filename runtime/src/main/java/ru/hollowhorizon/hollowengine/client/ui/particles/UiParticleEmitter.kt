package ru.hollowhorizon.hollowengine.client.ui.particles

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import kotlin.random.Random

/** Mutable, pooled UI particle. Distances are UI pixels and time is measured in seconds. */
class UiParticle internal constructor() {
    var x = 0f
    var y = 0f
    var velocityX = 0f
    var velocityY = 0f
    var lifetime = 1f
    var size = 8f
    var color = UiColor.White
    var age = 0f
        internal set
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    internal var previousX = 0f
    internal var previousY = 0f
    internal var previousAge = 0f
    internal var randomFrame = 0f
    internal var emitterIndex = 0

    internal fun reset() {
        x = 0f
        y = 0f
        velocityX = 0f
        velocityY = 0f
        lifetime = 1f
        size = 8f
        color = UiColor.White
        age = 0f
        previousAge = 0f
    }
}

/** The current drawing area's size; spawn positions are relative to its top-left corner. */
class UiParticleSpawnScope internal constructor(val random: Random) {
    var width = 0f
        internal set
    var height = 0f
        internal set
}

fun interface UiParticleCurve {
    fun sample(progress: Float): Float

    companion object {
        val Constant = UiParticleCurve { 1f }
        val FadeOut = UiParticleCurve { 1f - it }

        fun linear(start: Float, end: Float) = UiParticleCurve { start + (end - start) * it }
    }
}

enum class UiParticleAnimation { OVER_LIFETIME, RANDOM_FRAME }

data class UiParticleEmitter(
    val particle: String,
    val rate: Float = 12f,
    val initialCount: Int = 0,
    val animation: UiParticleAnimation = UiParticleAnimation.OVER_LIFETIME,
    val accelerationX: Float = 0f,
    val accelerationY: Float = 0f,
    val drag: Float = 0f,
    val scale: UiParticleCurve = UiParticleCurve.Constant,
    val alpha: UiParticleCurve = UiParticleCurve.FadeOut,
    val spawn: UiParticle.(UiParticleSpawnScope) -> Unit,
    val update: UiParticle.(deltaSeconds: Float) -> Unit = {},
) {
    internal val location: ResourceLocation by lazy { ResourceLocation.parse(particle) }

    init {
        require(particle.isNotBlank()) { "Particle id must not be blank" }
        require(rate.isFinite() && rate >= 0f) { "Emission rate must be finite and non-negative" }
        require(initialCount >= 0) { "Initial count must be non-negative" }
        require(accelerationX.isFinite() && accelerationY.isFinite()) { "Acceleration must be finite" }
        require(drag.isFinite() && drag >= 0f) { "Drag must be finite and non-negative" }
    }

    companion object {
        /** A rising smoke preset. Copy it to change rate, sprite set, curves or motion. */
        fun smoke(particle: String = "minecraft:smoke") = UiParticleEmitter(
            particle = particle,
            accelerationY = -4f,
            drag = 0.3f,
            scale = UiParticleCurve.linear(0.5f, 2f),
            spawn = { area ->
                x = area.random.nextFloat() * area.width
                y = area.height
                velocityX = (area.random.nextFloat() - 0.5f) * 12f
                velocityY = -12f - area.random.nextFloat() * 12f
                lifetime = 2f + area.random.nextFloat() * 2f
                size = 12f + area.random.nextFloat() * 12f
                color = UiColor(0.7f, 0.7f, 0.7f, 0.65f)
            },
        )
    }
}
